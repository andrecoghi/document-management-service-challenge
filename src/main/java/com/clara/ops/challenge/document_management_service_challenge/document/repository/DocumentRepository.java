package com.clara.ops.challenge.document_management_service_challenge.document.repository;

import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository
    extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {
  @Override
  @EntityGraph(attributePaths = "tags")
  Page<DocumentEntity> findAll(Specification<DocumentEntity> spec, Pageable pageable);
}
