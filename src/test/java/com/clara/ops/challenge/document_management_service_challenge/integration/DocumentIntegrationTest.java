package com.clara.ops.challenge.document_management_service_challenge.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.document.service.DocumentService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Disabled("Integration tests require Docker and may be unstable in CI")
@Testcontainers
@SpringBootTest
class DocumentIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15")
          .withDatabaseName("challenge")
          .withUsername("postgres")
          .withPassword("postgres")
          .withInitScript("schema-init.sql");

  @Container
  static GenericContainer<?> minio =
      new GenericContainer<>("minio/minio:latest")
          .withEnv("MINIO_ROOT_USER", "minioadmin")
          .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
          .withCommand("server /data")
          .withExposedPorts(9000);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=document_schema");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add(
        "minio.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getFirstMappedPort());
    registry.add("minio.access-key", () -> "minioadmin");
    registry.add("minio.secret-key", () -> "minioadmin");
    registry.add("document.storage.bucket", () -> "test-bucket");
  }

  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;

  @Test
  void fullFlowUploadSearchDownload() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "Hello World".getBytes());
    PresignedUrlRequest request = new PresignedUrlRequest("user", "test", Set.of("tag"), 1L);

    DocumentEntity entity = documentService.uploadDocument(file, request);
    assertThat(documentRepository.findById(entity.getId())).isPresent();

    Page<DocumentEntity> page =
        documentService.searchDocuments(
            new DocumentSearchFilters("user", "test", Set.of("tag")), PageRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);

    String url = documentService.generateDownloadUrl(entity.getId());
    assertThat(url).contains(minio.getHost());
  }

  @Test
  void prepareAndConfirmUploadFlow() throws Exception {
    PresignedUrlRequest request = new PresignedUrlRequest("user", "test", Set.of("tag"), 1L);
    String presignUrl = documentService.getPresignedUrl(request);

    List<DocumentEntity> entries = documentRepository.findAll();
    assertThat(entries).hasSize(1);
    DocumentEntity created = entries.get(0);

    ConfirmUploadRequest confirmReq =
        new ConfirmUploadRequest(
            created.getUser(),
            created.getName(),
            created.getTags(),
            created.getObjectKey(),
            created.getSize(),
            created.getType());

    DocumentEntity confirmed = documentService.confirmUpload(confirmReq);
    assertThat(documentRepository.findById(confirmed.getId())).isPresent();
  }
}
