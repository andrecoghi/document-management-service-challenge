package com.clara.ops.challenge.document_management_service_challenge.document.mapper;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginatedDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginationMetadata;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

  private final DocumentRepository documentRepository;

  public DocumentMapper() {
    this.documentRepository = null;
  }

  @Autowired
  public DocumentMapper(DocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  public DocumentResponse toResponse(DocumentEntity entity) {
    return new DocumentResponse(
        entity.getId(),
        entity.getUser(),
        entity.getName(),
        entity.getTags(),
        entity.getSize(),
        entity.getType(),
        entity.getCreatedAt());
  }

  public PaginatedDocumentResponse toPaginatedResponse(Page<DocumentEntity> page) {
    List<DocumentResponse> documents =
        page.getContent().stream().map(this::toResponse).collect(Collectors.toList());
    long totalElements =
        documentRepository != null ? documentRepository.count() : page.getTotalElements();
    int totalPages =
        documentRepository != null
            ? (int) Math.ceil((double) totalElements / page.getSize())
            : page.getTotalPages();
    PaginationMetadata metadata =
        new PaginationMetadata(
            page.getNumber(),
            page.getSize(),
            page.getNumberOfElements(),
            totalPages,
            totalElements);
    return new PaginatedDocumentResponse(metadata, documents);
  }
}
