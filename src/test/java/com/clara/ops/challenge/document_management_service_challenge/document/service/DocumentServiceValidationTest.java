package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentServiceValidationTest {

  private DocumentService documentService;

  @BeforeEach
  void setUp() {
    DocumentRepository mockRepo = mock(DocumentRepository.class);
    DocumentStorage mockStorage = mock(DocumentStorage.class);
    DocumentStorageProperties props = new DocumentStorageProperties();
    props.setBucket("bucket");
    props.setPrefix("");
    documentService = new DocumentService(mockRepo, mockStorage, props);
  }

  @Test
  void uploadDocument_emptyFile_throwsInvalidRequest() {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
    PresignedUrlRequest request = new PresignedUrlRequest("user", "name", Set.of(), 1L);
    assertThatThrownBy(() -> documentService.uploadDocument(emptyFile, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A non-empty PDF file must be provided");
  }

  @Test
  void uploadDocument_largeFile_throwsInvalidRequest() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(501L * 1024L * 1024L);
    when(file.getContentType()).thenReturn("application/pdf");
    PresignedUrlRequest request = new PresignedUrlRequest("user", "name", Set.of(), 1L);
    assertThatThrownBy(() -> documentService.uploadDocument(file, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("File size exceeds the 500MB limit");
  }

  @Test
  void uploadDocument_nullRequest_throwsInvalidRequest() {
    MockMultipartFile pdf =
        new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] {1});
    assertThatThrownBy(() -> documentService.uploadDocument(pdf, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Upload metadata is required");
  }

  @Test
  void uploadDocument_blankUser_throwsInvalidRequest() {
    MockMultipartFile pdf =
        new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] {1});
    PresignedUrlRequest request = new PresignedUrlRequest("   ", "doc", Set.of(), 1L);
    assertThatThrownBy(() -> documentService.uploadDocument(pdf, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("User is required");
  }

  @Test
  void uploadDocument_blankName_throwsInvalidRequest() {
    MockMultipartFile pdf =
        new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] {1});
    PresignedUrlRequest request = new PresignedUrlRequest("user", "  ", Set.of(), 1L);
    assertThatThrownBy(() -> documentService.uploadDocument(pdf, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Document name is required");
  }
}
