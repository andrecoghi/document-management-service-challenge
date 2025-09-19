package com.clara.ops.challenge.document_management_service_challenge.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentDownloadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginatedDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.UploadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentIntegrationTest {

  private static final byte[] SAMPLE_PDF_CONTENT =
      "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n"
          .getBytes(StandardCharsets.US_ASCII);

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("challenge")
          .withUsername("postgres")
          .withPassword("postgres")
          .withInitScript("schema-init.sql");

  @Container
  static final GenericContainer<?> MINIO =
      new GenericContainer<>("quay.io/minio/minio:RELEASE.2024-05-28T17-19-04Z")
          .withEnv("MINIO_ROOT_USER", "minioadmin")
          .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
          .withCommand("server /data --console-address :9001")
          .withExposedPorts(9000)
          .waitingFor(
              Wait.forHttp("/minio/health/ready")
                  .forPort(9000)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofSeconds(60)));

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> {
          String baseUrl = POSTGRES.getJdbcUrl();
          return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=document_schema";
        });
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add(
        "minio.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    registry.add("minio.access-key", () -> "minioadmin");
    registry.add("minio.secret-key", () -> "minioadmin");
    registry.add("minio.secure", () -> false);
    registry.add("document.storage.bucket", () -> "integration-bucket");
    registry.add("document.storage.prefix", () -> "integration");
    registry.add("document.storage.presigned-url-expiry-seconds", () -> 3600);
  }

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private DocumentRepository documentRepository;

  private final RestTemplate externalHttpClient = new RestTemplate();

  @Test
  void fullDocumentLifecycleThroughController() {
    PresignedUrlRequest uploadMetadata =
        new PresignedUrlRequest(
            "integration-user",
            "Quarterly Report.pdf",
            Set.of("finance", "quarterly"),
            (long) SAMPLE_PDF_CONTENT.length);

    ResponseEntity<UploadUrlResponse> uploadResponse =
        restTemplate.postForEntity(
            "/document-management/documents/uploads", uploadMetadata, UploadUrlResponse.class);

    assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UploadUrlResponse uploadBody = uploadResponse.getBody();
    assertThat(uploadBody).isNotNull();
    String uploadUrl = uploadBody.url();
    assertThat(uploadUrl).isNotBlank();

    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.setContentType(MediaType.APPLICATION_PDF);
    putHeaders.setContentLength(SAMPLE_PDF_CONTENT.length);
    HttpEntity<byte[]> putRequest = new HttpEntity<>(SAMPLE_PDF_CONTENT, putHeaders);

    ResponseEntity<String> putResponse =
        externalHttpClient.exchange(
            URI.create(uploadUrl), HttpMethod.PUT, putRequest, String.class);

    assertThat(putResponse.getStatusCode().is2xxSuccessful()).isTrue();

    ConfirmUploadRequest confirmUploadRequest =
        new ConfirmUploadRequest(
            uploadMetadata.user(),
            uploadMetadata.fileName(),
            uploadMetadata.tags(),
            uploadUrl,
            SAMPLE_PDF_CONTENT.length,
            MediaType.APPLICATION_PDF_VALUE);

    ResponseEntity<DocumentResponse> confirmResponse =
        restTemplate.postForEntity(
            "/document-management/documents", confirmUploadRequest, DocumentResponse.class);

    assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DocumentResponse confirmedDocument = confirmResponse.getBody();
    assertThat(confirmedDocument).isNotNull();
    assertThat(confirmedDocument.id()).isNotNull();
    assertThat(confirmedDocument.user()).isEqualTo(uploadMetadata.user());
    assertThat(confirmedDocument.name()).isEqualTo(uploadMetadata.fileName());
    assertThat(confirmedDocument.tags()).containsExactlyInAnyOrderElementsOf(uploadMetadata.tags());

    DocumentEntity persisted = documentRepository.findById(confirmedDocument.id()).orElseThrow();
    assertThat(persisted.getBucket()).isEqualTo("integration-bucket");
    assertThat(persisted.getObjectKey()).contains("integration/");

    DocumentSearchFilters filters = new DocumentSearchFilters(uploadMetadata.user(), null, null);
    ResponseEntity<PaginatedDocumentResponse> searchResponse =
        restTemplate.postForEntity(
            "/document-management/documents/search?page=0&size=5&sort=createdAt,desc",
            filters,
            PaginatedDocumentResponse.class);

    assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    PaginatedDocumentResponse searchBody = searchResponse.getBody();
    assertThat(searchBody).isNotNull();
    assertThat(searchBody.documents())
        .extracting(DocumentResponse::id)
        .contains(confirmedDocument.id());
    assertThat(searchBody.metadata().totalItems()).isGreaterThanOrEqualTo(1);

    ResponseEntity<DocumentDownloadUrlResponse> downloadUrlResponse =
        restTemplate.getForEntity(
            "/document-management/documents/" + confirmedDocument.id() + "/download",
            DocumentDownloadUrlResponse.class);

    assertThat(downloadUrlResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    DocumentDownloadUrlResponse downloadBody = downloadUrlResponse.getBody();
    assertThat(downloadBody).isNotNull();
    String downloadUrl = downloadBody.url();
    assertThat(downloadUrl).isNotBlank();

    ResponseEntity<byte[]> downloadedObject =
        externalHttpClient.getForEntity(URI.create(downloadUrl), byte[].class);

    assertThat(downloadedObject.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(downloadedObject.getBody()).containsExactly(SAMPLE_PDF_CONTENT);
  }
}
