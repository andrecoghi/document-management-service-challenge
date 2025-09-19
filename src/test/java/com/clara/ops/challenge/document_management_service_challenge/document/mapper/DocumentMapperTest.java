package com.clara.ops.challenge.document_management_service_challenge.document.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginatedDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginationMetadata;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DocumentMapperTest {

  private DocumentMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new DocumentMapper();
  }

  @Test
  void toResponse_mapsAllFields() {
    DocumentEntity entity = new DocumentEntity();
    UUID id = UUID.randomUUID();
    entity.setId(id);
    entity.setUser("user");
    entity.setName("name.pdf");
    entity.setTags(Set.of("a"));
    entity.setSize(123L);
    entity.setType("application/pdf");
    entity.setCreatedAt(Instant.EPOCH);

    DocumentResponse resp = mapper.toResponse(entity);
    assertThat(resp.id()).isEqualTo(id);
    assertThat(resp.user()).isEqualTo("user");
    assertThat(resp.name()).isEqualTo("name.pdf");
    assertThat(resp.tags()).containsExactly("a");
    assertThat(resp.size()).isEqualTo(123L);
    assertThat(resp.type()).isEqualTo("application/pdf");
    assertThat(resp.createdAt()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void toPaginatedResponse_mapsMetadataAndDocuments() {
    DocumentEntity e1 = new DocumentEntity();
    DocumentEntity e2 = new DocumentEntity();
    e1.setId(UUID.randomUUID());
    e2.setId(UUID.randomUUID());
    PageImpl<DocumentEntity> page = new PageImpl<>(List.of(e1, e2), PageRequest.of(1, 2), 5);

    PaginatedDocumentResponse resp = mapper.toPaginatedResponse(page);
    PaginationMetadata meta = resp.metadata();
    assertThat(meta.currentPage()).isEqualTo(1);
    assertThat(meta.itemsPerPage()).isEqualTo(2);
    assertThat(meta.currentItems()).isEqualTo(2);
    assertThat(meta.totalPages()).isEqualTo(3);
    assertThat(meta.totalItems()).isEqualTo(5);
    assertThat(resp.documents()).hasSize(2);
  }
}
