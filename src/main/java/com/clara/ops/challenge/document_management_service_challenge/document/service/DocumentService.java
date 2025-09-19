package com.clara.ops.challenge.document_management_service_challenge.document.service;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentSpecifications;
import com.clara.ops.challenge.document_management_service_challenge.document.validation.DocumentValidator;
import com.clara.ops.challenge.document_management_service_challenge.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class DocumentService {

  private static final Pattern SAFE_SEGMENT_PATTERN = Pattern.compile("[^a-zA-Z0-9-_.]");

  private final DocumentRepository documentRepository;
  private final DocumentStorage documentStorage;
  private final DocumentStorageProperties storageProperties;
  private final DocumentValidator documentValidator;

  /** Backward-compatible constructor for tests without a DocumentValidator. */
  public DocumentService(
      DocumentRepository documentRepository,
      DocumentStorage documentStorage,
      DocumentStorageProperties storageProperties) {
    this(documentRepository, documentStorage, storageProperties, new DocumentValidator());
  }

  @Transactional(readOnly = true)
  public Page<DocumentEntity> searchDocuments(DocumentSearchFilters filters, Pageable pageable) {
    log.info("Searching documents with filters: {} and pageable: {}", filters, pageable);
    return documentRepository.findAll(
        DocumentSpecifications.withFilters(
            Optional.ofNullable(filters).map(DocumentSearchFilters::user).orElse(null),
            Optional.ofNullable(filters).map(DocumentSearchFilters::fileName).orElse(null),
            Optional.ofNullable(filters).map(DocumentSearchFilters::tags).orElse(null)),
        pageable);
  }

  @Transactional(readOnly = true)
  public DocumentEntity getDocument(UUID id) {
    log.info("Retrieving document with id {}", id);
    return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public String generateDownloadUrl(UUID documentId) {
    var document = getDocument(documentId);
    log.info(
        "Generating download URL for documentId={}, objectKey={}",
        documentId,
        document.getObjectKey());
    try {
      return documentStorage.generatePresignedGetUrl(document.getObjectKey());
    } catch (Exception e) {
      throw new DocumentNotFoundException(documentId);
    }
  }

  @Transactional(readOnly = true)
  public String generatePresignedPutUrl(String objectKey) {
    log.info("Generating presigned PUT URL for objectKey={}", objectKey);
    return documentStorage.generatePresignedPutUrl(objectKey);
  }

  private Set<String> normalizeTags(Set<String> tags) {
    return tags.stream()
        .filter(StringUtils::hasText)
        .map(String::trim)
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String normalizeSegment(String value) {
    var trimmed = value.trim().toLowerCase().replace(' ', '-');
    return SAFE_SEGMENT_PATTERN.matcher(trimmed).replaceAll("-");
  }

  private String normalizeFileName(String name) {
    var trimmed = name.trim().toLowerCase();
    if (!trimmed.endsWith(".pdf")) {
      trimmed = trimmed + ".pdf";
    }
    return SAFE_SEGMENT_PATTERN.matcher(trimmed.replace(' ', '-')).replaceAll("-");
  }

  private String buildObjectKey(String normalizedUser, String normalizedName) {
    var uniqueId = UUID.randomUUID().toString();
    var baseKey =
        StringUtils.hasText(normalizedUser)
            ? normalizedUser + "/" + uniqueId + "-" + normalizedName
            : uniqueId + "-" + normalizedName;
    var prefix = storageProperties.getPrefix();
    if (StringUtils.hasText(prefix)) {
      var normalizedPrefix =
          prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
      return normalizedPrefix + "/" + baseKey;
    }
    return baseKey;
  }

  @Transactional
  public String getPresignedUrl(PresignedUrlRequest request) {
    log.info(
        "Generating presigned PUT URL for user={}, fileName={}",
        request != null ? request.user() : null,
        request != null ? request.fileName() : null);
    if (request == null) {
      log.warn("Invalid presigned URL request: request metadata is null");
      throw new InvalidRequestException("Upload metadata is required");
    }
    if (!StringUtils.hasText(request.user())) {
      log.warn("Invalid presigned URL request: user is empty");
      throw new InvalidRequestException("User is required");
    }
    if (!StringUtils.hasText(request.fileName())) {
      log.warn("Invalid presigned URL request: fileName is empty for user {}", request.user());
      throw new InvalidRequestException("Document name is required");
    }
    if (request.fileSize() == null || request.fileSize() <= 0) {
      log.warn("Invalid presigned URL request: fileSize {} is not positive", request.fileSize());
      throw new InvalidRequestException("File size must be positive");
    }
    var normalizedUser = normalizeSegment(request.user());
    var normalizedName = normalizeFileName(request.fileName());
    var objectKey = buildReadableObjectKey(normalizedUser, normalizedName);
    return documentStorage.generatePresignedPutUrl(objectKey);
  }

  public DocumentEntity confirmUpload(ConfirmUploadRequest request) {
    log.info(
        "Confirming upload for user={}, fileName={}, objectKey={}, fileSize={}, contentType={}",
        request != null ? request.user() : null,
        request != null ? request.fileName() : null,
        request != null ? request.objectKey() : null,
        request != null ? request.fileSize() : null,
        request != null ? request.contentType() : null);
    documentValidator.confirmUpload(request);
    var entity = new DocumentEntity();
    entity.setUser(request.user().trim());
    entity.setName(request.fileName().trim());
    entity.setBucket(documentStorage.getBucket());
    entity.setType(request.contentType());
    entity.setSize(request.fileSize());
    entity.setCreatedAt(Instant.now());
    entity.setTags(normalizeTags(request.tags()));
    var rawKey = request.objectKey();
    String finalKey;
    try {
      var uri = new URI(rawKey);
      var path = uri.getPath();
      var bucketPath = "/" + documentStorage.getBucket() + "/";
      finalKey = path.startsWith(bucketPath) ? path.substring(bucketPath.length()) : path;
    } catch (URISyntaxException e) {
      finalKey = rawKey;
    }
    entity.setObjectKey(finalKey);
    return documentRepository.save(entity);
  }

  private String buildReadableObjectKey(String normalizedUser, String normalizedName) {
    var readableKey =
        StringUtils.hasText(normalizedUser)
            ? normalizedUser + "/" + normalizedName
            : normalizedName;
    String prefix = storageProperties.getPrefix();
    if (StringUtils.hasText(prefix)) {
      String normalizedPrefix =
          prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
      return normalizedPrefix + "/" + readableKey;
    }
    return readableKey;
  }
}
