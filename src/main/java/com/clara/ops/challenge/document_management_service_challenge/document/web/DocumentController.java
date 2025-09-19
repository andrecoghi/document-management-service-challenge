package com.clara.ops.challenge.document_management_service_challenge.document.web;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentDownloadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginatedDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.UploadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.mapper.DocumentMapper;
import com.clara.ops.challenge.document_management_service_challenge.document.service.DocumentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/document-management")
@Validated
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

  private final DocumentService documentService;
  private final DocumentMapper documentMapper;

  @PostMapping(path = "/documents/uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadUrlResponse initiateUpload(@Valid @RequestBody PresignedUrlRequest metadata) {
    var url = documentService.getPresignedUrl(metadata);
    return new UploadUrlResponse(url);
  }

  @PostMapping(path = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentResponse confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
    return documentMapper.toResponse(documentService.confirmUpload(request));
  }

  @PostMapping(path = "/documents/search", consumes = MediaType.APPLICATION_JSON_VALUE)
  public PaginatedDocumentResponse searchDocuments(
      @RequestBody(required = false) DocumentSearchFilters filters,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    var sanitizedPageable = sanitizePageable(pageable);
    return documentMapper.toPaginatedResponse(
        documentService.searchDocuments(filters, sanitizedPageable));
  }

  @GetMapping(path = "/documents/{documentId}/download")
  public DocumentDownloadUrlResponse downloadDocument(@PathVariable UUID documentId) {
    var url = documentService.generateDownloadUrl(documentId);
    return new DocumentDownloadUrlResponse(url);
  }

  private Pageable sanitizePageable(Pageable incoming) {
    int page = Math.max(incoming.getPageNumber(), 0);
    int rawSize = incoming.getPageSize();
    int size = Math.min(Math.max(rawSize, 1), 100);
    Sort sort = incoming.getSort();
    if (sort == null || sort.isUnsorted()) {
      sort = Sort.by(Sort.Order.desc("createdAt"));
    }
    if (page != incoming.getPageNumber() || size != rawSize) {
      log.debug(
          "Adjusted pageable parameters from page={}, size={} to page={}, size={}.",
          incoming.getPageNumber(),
          rawSize,
          page,
          size);
    }
    return PageRequest.of(page, size, sort);
  }
}
