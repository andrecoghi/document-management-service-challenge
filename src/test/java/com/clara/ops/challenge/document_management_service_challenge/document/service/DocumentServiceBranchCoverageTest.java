package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceBranchCoverageTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocumentStorage documentStorage;

  private DocumentStorageProperties storageProperties;
  private DocumentService documentService;

  @BeforeEach
  void setUp() {
    storageProperties = new DocumentStorageProperties();
    storageProperties.setBucket("bucket");
    storageProperties.setPrefix("archive");
    documentService = new DocumentService(documentRepository, documentStorage, storageProperties);
  }

  @Test
  void uploadDocument_nullFile_throwsInvalidRequest() {
    PresignedUrlRequest request = new PresignedUrlRequest("user", "doc", Set.of(), 1L);
    assertThatThrownBy(() -> documentService.uploadDocument(null, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A non-empty PDF file must be provided");
  }

  @Test
  void uploadDocument_handlesIOExceptionDuringStreaming() throws IOException {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(1L);
    when(file.getContentType()).thenReturn("application/pdf");
    when(file.getInputStream()).thenThrow(new IOException("stream error"));
    PresignedUrlRequest request = new PresignedUrlRequest("user", "doc", Set.of(), 1L);
    when(documentStorage.getBucket()).thenReturn("bucket");
    assertThatThrownBy(() -> documentService.uploadDocument(file, request))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Unable to read uploaded file");
  }

  @Test
  void uploadDocument_defaultContentType_whenContentTypeMissing() throws IOException {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(2L);
    when(file.getContentType()).thenReturn("");
    when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
    PresignedUrlRequest request = new PresignedUrlRequest("User Test", "TestDoc.PDF", Set.of(), 1L);
    when(documentStorage.getBucket()).thenReturn("bucket");
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    DocumentEntity result = documentService.uploadDocument(file, request);
    assertThat(result.getType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
  }

  @Test
  void uploadDocument_noPrefix_buildsKeyWithoutPrefix() {
    storageProperties.setPrefix("");
    MockMultipartFile pdf =
        new MockMultipartFile("file", "DocName", "application/pdf", new byte[] {1});
    PresignedUrlRequest request = new PresignedUrlRequest("User Name", "DocName", Set.of(), 1L);
    when(documentStorage.getBucket()).thenReturn("bucket");
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    DocumentEntity result = documentService.uploadDocument(pdf, request);
    assertThat(result.getObjectKey()).matches("user-name/[a-f0-9\\-]{36}-docname\\.pdf");
  }

  @Test
  void uploadDocument_withPrefix_buildsKeyWithPrefix() {
    storageProperties.setPrefix("archive");
    MockMultipartFile pdf =
        new MockMultipartFile("file", "DocName", "application/pdf", new byte[] {1});
    PresignedUrlRequest request = new PresignedUrlRequest("User Name", "DocName", Set.of(), 1L);
    when(documentStorage.getBucket()).thenReturn("bucket");
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    DocumentEntity result = documentService.uploadDocument(pdf, request);
    assertThat(result.getObjectKey()).matches("archive/user-name/[a-f0-9\\-]{36}-docname\\.pdf");
  }

  @Test
  void uploadDocument_withTrailingSlashPrefix_buildsKeyWithNormalizedPrefix() {
    storageProperties.setPrefix("archive/");
    MockMultipartFile pdf =
        new MockMultipartFile("file", "DocName", "application/pdf", new byte[] {1});
    PresignedUrlRequest request = new PresignedUrlRequest("User Name", "DocName", Set.of(), 1L);
    when(documentStorage.getBucket()).thenReturn("bucket");
    when(documentRepository.save(any(DocumentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    DocumentEntity result = documentService.uploadDocument(pdf, request);
    assertThat(result.getObjectKey()).matches("archive/user-name/[a-f0-9\\-]{36}-docname\\.pdf");
  }
}
