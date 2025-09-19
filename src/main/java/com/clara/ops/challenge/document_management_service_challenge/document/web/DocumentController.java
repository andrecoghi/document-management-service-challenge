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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/document-management")
@Validated
public class DocumentController {

  private final DocumentService documentService;
  private final DocumentMapper documentMapper;

  public DocumentController(DocumentService documentService, DocumentMapper documentMapper) {
    this.documentService = documentService;
    this.documentMapper = documentMapper;
  }

  @PostMapping(path = "/documents/uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadUrlResponse initiateUpload(@Valid @RequestBody PresignedUrlRequest metadata) {
    String url = documentService.getPresignedUrl(metadata);
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
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(name = "sort", required = false) List<String> sortParams) {
    Pageable pageable = PageRequest.of(page, size, buildSort(sortParams));
    return documentMapper.toPaginatedResponse(documentService.searchDocuments(filters, pageable));
  }

  @GetMapping(path = "/documents/{documentId}/download")
  public DocumentDownloadUrlResponse downloadDocument(@PathVariable UUID documentId) {
    String url = documentService.generateDownloadUrl(documentId);
    return new DocumentDownloadUrlResponse(url);
  }

  private Sort buildSort(List<String> sortParams) {
    if (sortParams == null || sortParams.isEmpty()) {
      return Sort.by(Sort.Order.desc("createdAt"));
    }

    List<Sort.Order> orders = new java.util.ArrayList<>();
    for (String param : sortParams) {
      if (param == null || param.isBlank()) {
        continue;
      }
      String[] tokens = param.split(",");
      String property = tokens[0].trim();
      Sort.Direction direction = Sort.Direction.DESC;
      if (tokens.length > 1) {
        try {
          direction = Sort.Direction.fromString(tokens[1].trim());
        } catch (IllegalArgumentException ignored) {
          direction = Sort.Direction.DESC;
        }
      }
      orders.add(new Sort.Order(direction, property));
    }

    if (orders.isEmpty()) {
      orders.add(Sort.Order.desc("createdAt"));
    }
    return Sort.by(orders);
  }
}
