package com.clara.ops.challenge.document_management_service_challenge.document.service;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentSpecifications;
import com.clara.ops.challenge.document_management_service_challenge.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DocumentService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentService.class);

  private static final long MAX_FILE_SIZE_BYTES = 500L * 1024L * 1024L;
  private static final Pattern SAFE_SEGMENT_PATTERN = Pattern.compile("[^a-zA-Z0-9-_.]");

  private final DocumentRepository documentRepository;
  private final DocumentStorage documentStorage;
  private final DocumentStorageProperties storageProperties;

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentStorage documentStorage,
      DocumentStorageProperties storageProperties) {
    this.documentRepository = documentRepository;
    this.documentStorage = documentStorage;
    this.storageProperties = storageProperties;
  }

  public DocumentEntity uploadDocument(MultipartFile file, PresignedUrlRequest request) {
    validateUpload(file, request);
    LOGGER.info(
        "Uploading document: user={}, fileName={}, size={}",
        request.user(),
        request.fileName(),
        file.getSize());

    String normalizedUser = normalizeSegment(request.user());
    String normalizedName = normalizeFileName(request.fileName());

    DocumentEntity entity = new DocumentEntity();
    entity.setUser(request.user().trim());
    entity.setName(request.fileName().trim());
    entity.setBucket(documentStorage.getBucket());
    entity.setType(resolveContentType(file));
    entity.setSize(file.getSize());
    entity.setCreatedAt(Instant.now());
    entity.setTags(normalizeTags(Optional.ofNullable(request.tags()).orElse(Set.of())));

    String objectKey = buildObjectKey(normalizedUser, normalizedName);

    try (InputStream inputStream = file.getInputStream()) {
      documentStorage.upload(objectKey, inputStream, file.getSize(), entity.getType());
      entity.setObjectKey(objectKey);
      return documentRepository.save(entity);
    } catch (IOException exception) {
      LOGGER.error(
          "Unable to read uploaded file for user={}, fileName={}",
          request.user(),
          request.fileName(),
          exception);
      throw new StorageException("Unable to read uploaded file", exception);
    }
  }

  @Transactional(readOnly = true)
  public Page<DocumentEntity> searchDocuments(DocumentSearchFilters filters, Pageable pageable) {
    LOGGER.info("Searching documents with filters: {} and pageable: {}", filters, pageable);
    return documentRepository.findAll(
        DocumentSpecifications.withFilters(
            Optional.ofNullable(filters).map(DocumentSearchFilters::user).orElse(null),
            Optional.ofNullable(filters).map(DocumentSearchFilters::fileName).orElse(null),
            Optional.ofNullable(filters).map(DocumentSearchFilters::tags).orElse(null)),
        pageable);
  }

  @Transactional(readOnly = true)
  public DocumentEntity getDocument(UUID id) {
    LOGGER.info("Retrieving document with id {}", id);
    return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public String generateDownloadUrl(UUID documentId) {
    DocumentEntity document = getDocument(documentId);
    LOGGER.info(
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
    LOGGER.info("Generating presigned PUT URL for objectKey={}", objectKey);
    return documentStorage.generatePresignedPutUrl(objectKey);
  }

  private void validateUpload(MultipartFile file, PresignedUrlRequest request) {
    LOGGER.info(
        "Validating upload for user={}, fileName={}, fileSize={}",
        request != null ? request.user() : null,
        request != null ? request.fileName() : null,
        file != null ? file.getSize() : 0);
    if (file == null || file.isEmpty()) {
      throw new InvalidRequestException("A non-empty PDF file must be provided");
    }
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new InvalidRequestException("File size exceeds the 500MB limit");
    }
    if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(resolveContentType(file))) {
      throw new InvalidRequestException("Only PDF documents are supported");
    }
    if (request == null) {
      throw new InvalidRequestException("Upload metadata is required");
    }
    if (!StringUtils.hasText(request.user())) {
      throw new InvalidRequestException("User is required");
    }
    if (!StringUtils.hasText(request.fileName())) {
      throw new InvalidRequestException("Document name is required");
    }
  }

  private String resolveContentType(MultipartFile file) {
    String contentType = file.getContentType();
    if (!StringUtils.hasText(contentType)) {
      return MediaType.APPLICATION_PDF_VALUE;
    }
    return contentType;
  }

  private Set<String> normalizeTags(Set<String> tags) {
    return tags.stream()
        .filter(StringUtils::hasText)
        .map(String::trim)
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String normalizeSegment(String value) {
    String trimmed = value.trim().toLowerCase().replace(' ', '-');
    return SAFE_SEGMENT_PATTERN.matcher(trimmed).replaceAll("-");
  }

  private String normalizeFileName(String name) {
    String trimmed = name.trim().toLowerCase();
    if (!trimmed.endsWith(".pdf")) {
      trimmed = trimmed + ".pdf";
    }
    return SAFE_SEGMENT_PATTERN.matcher(trimmed.replace(' ', '-')).replaceAll("-");
  }

  private String buildObjectKey(String normalizedUser, String normalizedName) {
    // Gera um UUID para garantir unicidade
    String uniqueId = UUID.randomUUID().toString();
    String baseKey =
        StringUtils.hasText(normalizedUser)
            ? normalizedUser + "/" + uniqueId + "-" + normalizedName
            : uniqueId + "-" + normalizedName;
    String prefix = storageProperties.getPrefix();
    if (StringUtils.hasText(prefix)) {
      String normalizedPrefix =
          prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
      return normalizedPrefix + "/" + baseKey;
    }
    return baseKey;
  }

  @Transactional
  public String getPresignedUrl(PresignedUrlRequest request) {
    LOGGER.info(
        "Generating presigned PUT URL for user={}, fileName={}",
        request != null ? request.user() : null,
        request != null ? request.fileName() : null);
    if (request == null) {
      LOGGER.warn("Invalid presigned URL request: request metadata is null");
      throw new InvalidRequestException("Upload metadata is required");
    }
    if (!StringUtils.hasText(request.user())) {
      LOGGER.warn("Invalid presigned URL request: user is empty");
      throw new InvalidRequestException("User is required");
    }
    if (!StringUtils.hasText(request.fileName())) {
      LOGGER.warn("Invalid presigned URL request: fileName is empty for user {}", request.user());
      throw new InvalidRequestException("Document name is required");
    }
    if (request.fileSize() == null || request.fileSize() <= 0) {
      LOGGER.warn("Invalid presigned URL request: fileSize {} is not positive", request.fileSize());
      throw new InvalidRequestException("File size must be positive");
    }
    String normalizedUser = normalizeSegment(request.user());
    String normalizedName = normalizeFileName(request.fileName());
    String objectKey = buildReadableObjectKey(normalizedUser, normalizedName);
    return documentStorage.generatePresignedPutUrl(objectKey);
  }

  public DocumentEntity confirmUpload(ConfirmUploadRequest request) {
    LOGGER.info(
        "Confirming upload for user={}, fileName={}, objectKey={}, fileSize={}, contentType={}",
        request != null ? request.user() : null,
        request != null ? request.fileName() : null,
        request != null ? request.objectKey() : null,
        request != null ? request.fileSize() : null,
        request != null ? request.contentType() : null);
    if (request == null) {
      LOGGER.warn("Invalid confirm upload request: request metadata is null");
      throw new InvalidRequestException("Confirm upload metadata is required");
    }
    if (!StringUtils.hasText(request.user())) {
      LOGGER.warn("Invalid confirm upload request: user is empty");
      throw new InvalidRequestException("User is required");
    }
    if (!StringUtils.hasText(request.fileName())) {
      LOGGER.warn("Invalid confirm upload request: fileName is empty for user {}", request.user());
      throw new InvalidRequestException("Document name is required");
    }
    if (!StringUtils.hasText(request.objectKey())) {
      LOGGER.warn("Invalid confirm upload request: objectKey is empty for user {}", request.user());
      throw new InvalidRequestException("Object key is required");
    }
    if (request.fileSize() <= 0) {
      LOGGER.warn(
          "Invalid confirm upload request: fileSize {} is not positive", request.fileSize());
      throw new InvalidRequestException("File size must be positive");
    }
    if (!StringUtils.hasText(request.contentType())) {
      LOGGER.warn(
          "Invalid confirm upload request: contentType is empty for user {}", request.user());
      throw new InvalidRequestException("File type is required");
    }
    DocumentEntity entity = new DocumentEntity();
    entity.setUser(request.user().trim());
    entity.setName(request.fileName().trim());
    entity.setBucket(documentStorage.getBucket());
    entity.setType(request.contentType());
    entity.setSize(request.fileSize());
    entity.setCreatedAt(Instant.now());
    entity.setTags(normalizeTags(request.tags()));
    String rawKey = request.objectKey();
    String finalKey;
    try {
      URI uri = new URI(rawKey);
      String path = uri.getPath();
      String bucketPath = "/" + documentStorage.getBucket() + "/";
      finalKey = path.startsWith(bucketPath) ? path.substring(bucketPath.length()) : path;
    } catch (URISyntaxException e) {
      finalKey = rawKey;
    }
    entity.setObjectKey(finalKey);
    return documentRepository.save(entity);
  }

  private String buildReadableObjectKey(String normalizedUser, String normalizedName) {
    String readableKey =
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
