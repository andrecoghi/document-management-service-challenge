package com.clara.ops.challenge.document_management_service_challenge.document.validation;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentValidatorTest {

  private DocumentValidator validator;

  @BeforeEach
  void setUp() {
    validator = new DocumentValidator();
  }

  @Test
  void validateUploadRejectsNullFile() {
    PresignedUrlRequest request = new PresignedUrlRequest("user", "file", Set.of("tag"), 123L);

    assertThatThrownBy(() -> validator.validateUpload(null, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("non-empty PDF file");
  }

  @Test
  void validateUploadRejectsEmptyFile() {
    MultipartFile file = Mockito.mock(MultipartFile.class);
    Mockito.when(file.isEmpty()).thenReturn(true);

    PresignedUrlRequest request = new PresignedUrlRequest("user", "file", Set.of("tag"), 123L);

    assertThatThrownBy(() -> validator.validateUpload(file, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("non-empty PDF file");
  }

  @Test
  void validateUploadRejectsOversizedFile() {
    MultipartFile file = Mockito.mock(MultipartFile.class);
    Mockito.when(file.isEmpty()).thenReturn(false);
    Mockito.when(file.getSize()).thenReturn(501L * 1024 * 1024);
    Mockito.when(file.getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);

    PresignedUrlRequest request = new PresignedUrlRequest("user", "file", Set.of("tag"), 123L);

    assertThatThrownBy(() -> validator.validateUpload(file, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("500MB");
  }

  @Test
  void validateUploadRejectsNonPdfContentType() {
    MultipartFile file = Mockito.mock(MultipartFile.class);
    Mockito.when(file.isEmpty()).thenReturn(false);
    Mockito.when(file.getSize()).thenReturn(1024L);
    Mockito.when(file.getContentType()).thenReturn(MediaType.IMAGE_PNG_VALUE);

    PresignedUrlRequest request = new PresignedUrlRequest("user", "file", Set.of("tag"), 123L);

    assertThatThrownBy(() -> validator.validateUpload(file, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only PDF documents are supported");
  }

  @Test
  void validateUploadRejectsMissingMetadata() {
    MultipartFile file = Mockito.mock(MultipartFile.class);
    Mockito.when(file.isEmpty()).thenReturn(false);
    Mockito.when(file.getSize()).thenReturn(1024L);
    Mockito.when(file.getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);

    assertThatThrownBy(() -> validator.validateUpload(file, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Upload metadata is required");
  }

  @Test
  void validateUploadRejectsMissingUserOrName() {
    MultipartFile file = Mockito.mock(MultipartFile.class);
    Mockito.when(file.isEmpty()).thenReturn(false);
    Mockito.when(file.getSize()).thenReturn(1024L);
    Mockito.when(file.getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);

    PresignedUrlRequest missingUser = new PresignedUrlRequest(null, "file", Set.of("tag"), 123L);
    PresignedUrlRequest missingName = new PresignedUrlRequest("user", "", Set.of("tag"), 123L);

    assertThatThrownBy(() -> validator.validateUpload(file, missingUser))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("User is required");

    assertThatThrownBy(() -> validator.validateUpload(file, missingName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Document name is required");
  }

  @Test
  void validateUploadSucceedsWhenContentTypeMissingButPdfDataProvided() {
    MockMultipartFile file =
        new MockMultipartFile("file", "file.pdf", null, "dummy".getBytes(StandardCharsets.UTF_8));

    PresignedUrlRequest request = new PresignedUrlRequest("user", "name", Set.of(), 123L);

    assertThatNoException().isThrownBy(() -> validator.validateUpload(file, request));
  }

  @Test
  void confirmUploadRejectsMissingFields() {
    ConfirmUploadRequest missingUser =
        new ConfirmUploadRequest(
            null, "name", Set.of(), "key", 10L, MediaType.APPLICATION_PDF_VALUE);
    ConfirmUploadRequest missingName =
        new ConfirmUploadRequest("user", "", Set.of(), "key", 10L, MediaType.APPLICATION_PDF_VALUE);
    ConfirmUploadRequest missingKey =
        new ConfirmUploadRequest(
            "user", "name", Set.of(), "", 10L, MediaType.APPLICATION_PDF_VALUE);
    ConfirmUploadRequest nonPositiveSize =
        new ConfirmUploadRequest(
            "user", "name", Set.of(), "key", 0L, MediaType.APPLICATION_PDF_VALUE);
    ConfirmUploadRequest missingType =
        new ConfirmUploadRequest("user", "name", Set.of(), "key", 10L, "");

    assertThatThrownBy(() -> validator.confirmUpload(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Confirm upload metadata is required");
    assertThatThrownBy(() -> validator.confirmUpload(missingUser))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("User is required");
    assertThatThrownBy(() -> validator.confirmUpload(missingName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Document name is required");
    assertThatThrownBy(() -> validator.confirmUpload(missingKey))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Object key is required");
    assertThatThrownBy(() -> validator.confirmUpload(nonPositiveSize))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("File size must be positive");
    assertThatThrownBy(() -> validator.confirmUpload(missingType))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("File type is required");
  }

  @Test
  void confirmUploadSucceedsWhenAllFieldsPresent() {
    ConfirmUploadRequest request =
        new ConfirmUploadRequest(
            "user", "name", Set.of("tag"), "key", 42L, MediaType.APPLICATION_PDF_VALUE);

    assertThatNoException().isThrownBy(() -> validator.confirmUpload(request));
  }
}
