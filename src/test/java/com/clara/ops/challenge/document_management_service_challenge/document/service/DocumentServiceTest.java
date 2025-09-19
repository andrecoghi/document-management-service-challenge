package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
  @Test
  void getPresignedUrl_ReturnsUrl_WhenValidRequest() {
    PresignedUrlRequest req = new PresignedUrlRequest("user", "Doc", Set.of("tag1"), 1L);
    when(documentStorage.generatePresignedPutUrl(any())).thenReturn("http://minio/put-url");
    String url = documentService.getPresignedUrl(req);
    assertThat(url).isEqualTo("http://minio/put-url");
  }

  @Test
  void getPresignedUrl_Throws_WhenMissingUserOrName() {
    PresignedUrlRequest req1 = new PresignedUrlRequest(null, "Doc", Set.of(), 1L);
    PresignedUrlRequest req2 = new PresignedUrlRequest("user", null, Set.of(), 1L);
    assertThatThrownBy(() -> documentService.getPresignedUrl(null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.getPresignedUrl(req1))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.getPresignedUrl(req2))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void confirmUpload_SavesDocument_WhenValidRequest() {
    var req =
        new com.clara.ops.challenge.document_management_service_challenge.document.dto
            .ConfirmUploadRequest(
            "user", "Doc", Set.of("tag1"), "user/doc.pdf", 123L, "application/pdf");
    when(documentStorage.getBucket()).thenReturn("document-bucket");
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, DocumentEntity.class));
    DocumentEntity entity = documentService.confirmUpload(req);
    assertThat(entity.getUser()).isEqualTo("user");
    assertThat(entity.getName()).isEqualTo("Doc");
    assertThat(entity.getObjectKey()).isEqualTo("user/doc.pdf");
    assertThat(entity.getSize()).isEqualTo(123L);
    assertThat(entity.getType()).isEqualTo("application/pdf");
    assertThat(entity.getTags()).containsExactly("tag1");
    verify(documentRepository).save(any(DocumentEntity.class));
  }

  @Test
  void confirmUpload_Throws_WhenInvalidRequest() {
    var reqMissingUser =
        new com.clara.ops.challenge.document_management_service_challenge.document.dto
            .ConfirmUploadRequest(null, "Doc", Set.of(), "key", 1L, "application/pdf");
    var reqMissingName =
        new com.clara.ops.challenge.document_management_service_challenge.document.dto
            .ConfirmUploadRequest("user", null, Set.of(), "key", 1L, "application/pdf");
    var reqMissingKey =
        new com.clara.ops.challenge.document_management_service_challenge.document.dto
            .ConfirmUploadRequest("user", "Doc", Set.of(), null, 1L, "application/pdf");
    var reqZeroSize =
        new com.clara.ops.challenge.document_management_service_challenge.document.dto
            .ConfirmUploadRequest("user", "Doc", Set.of(), "key", 0L, "application/pdf");
    var reqMissingType =
        new com.clara.ops.challenge.document_management_service_challenge.document.dto
            .ConfirmUploadRequest("user", "Doc", Set.of(), "key", 1L, null);
    assertThatThrownBy(() -> documentService.confirmUpload(null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.confirmUpload(reqMissingUser))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.confirmUpload(reqMissingName))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.confirmUpload(reqMissingKey))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.confirmUpload(reqZeroSize))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> documentService.confirmUpload(reqMissingType))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Mock private DocumentRepository documentRepository;

  @Mock private DocumentStorage documentStorage;

  private DocumentStorageProperties storageProperties;

  private DocumentService documentService;

  @BeforeEach
  void setUp() {
    storageProperties = new DocumentStorageProperties();
    storageProperties.setBucket("document-bucket");
    storageProperties.setPrefix("archive");
    storageProperties.setPresignedUrlExpirySeconds(600);
    documentService = new DocumentService(documentRepository, documentStorage, storageProperties);
  }

  @Test
  void uploadDocument_StoresMetadataAndStreamsToStorage() {
    MockMultipartFile pdf =
        new MockMultipartFile("file", "sample.pdf", "application/pdf", new byte[] {1, 2, 3});
    PresignedUrlRequest request =
        new PresignedUrlRequest("User One", "Report", Set.of("Finance", "Q1"), 1L);

    when(documentStorage.getBucket()).thenReturn("document-bucket");
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, DocumentEntity.class));

    DocumentEntity result = documentService.uploadDocument(pdf, request);

    assertThat(result.getId()).isNull();
    assertThat(result.getBucket()).isEqualTo("document-bucket");
    assertThat(result.getObjectKey()).matches("archive/user-one/[a-f0-9\\-]{36}-report\\.pdf");
    assertThat(result.getTags()).containsExactlyInAnyOrder("finance", "q1");
    assertThat(result.getCreatedAt()).isNotNull();

    verify(documentStorage)
        .upload(
            argThat(key -> key.matches("archive/user-one/[a-f0-9\\-]{36}-report\\.pdf")),
            any(),
            eq(3L),
            eq("application/pdf"));
    verify(documentRepository).save(any(DocumentEntity.class));
  }

  @Test
  void uploadDocumentRejectsNonPdfFiles() {
    MockMultipartFile textFile =
        new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});
    PresignedUrlRequest request = new PresignedUrlRequest("user", "doc", Set.of(), 1L);

    assertThatThrownBy(() -> documentService.uploadDocument(textFile, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only PDF documents are supported");

    verify(documentStorage, never()).upload(any(), any(), anyLong(), any());
    verify(documentRepository, never()).save(any());
  }

  @Test
  void searchDocumentsDelegatesToRepositoryWithPageable() {
    Pageable pageable = PageRequest.of(0, 5);
    when(documentRepository.findAll(any(Specification.class), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(new DocumentEntity())));

    documentService.searchDocuments(
        new DocumentSearchFilters("user", "name", Set.of("tag")), pageable);

    verify(documentRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void generateDownloadUrlReturnsPresignedLink() {
    UUID id = UUID.randomUUID();
    DocumentEntity entity = new DocumentEntity();
    entity.setId(id);
    entity.setObjectKey("archive/user/report.pdf");
    entity.setCreatedAt(Instant.now());

    when(documentRepository.findById(id)).thenReturn(java.util.Optional.of(entity));
    when(documentStorage.generatePresignedGetUrl("archive/user/report.pdf"))
        .thenReturn("http://minio/presigned");

    String url = documentService.generateDownloadUrl(id);
    assertThat(url).isEqualTo("http://minio/presigned");
  }

  @Test
  void generateDownloadUrlThrowsWhenDocumentMissing() {
    UUID id = UUID.randomUUID();
    when(documentRepository.findById(id)).thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> documentService.generateDownloadUrl(id))
        .isInstanceOf(DocumentNotFoundException.class);
  }
}
