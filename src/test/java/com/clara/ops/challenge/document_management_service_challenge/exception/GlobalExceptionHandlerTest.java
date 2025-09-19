package com.clara.ops.challenge.document_management_service_challenge.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    request = org.mockito.Mockito.mock(HttpServletRequest.class);
    org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/test-uri");
  }

  @Test
  void handleInvalidRequest_buildsBadRequest() {
    InvalidRequestException ex = new InvalidRequestException("bad");
    ResponseEntity<ApiErrorResponse> resp = handler.handleInvalidRequest(ex, request);
    assertThat(resp.getStatusCodeValue()).isEqualTo(400);
    ApiErrorResponse body = resp.getBody();
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.message()).isEqualTo("bad");
    assertThat(body.path()).isEqualTo("/test-uri");
    assertThat(body.timestamp()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void handleDocumentNotFound_buildsNotFound() {
    DocumentNotFoundException ex = new DocumentNotFoundException(java.util.UUID.randomUUID());
    ResponseEntity<ApiErrorResponse> resp = handler.handleDocumentNotFound(ex, request);
    assertThat(resp.getStatusCodeValue()).isEqualTo(404);
    assertThat(resp.getBody().status()).isEqualTo(404);
  }

  @Test
  void handleStorageException_buildsServerError() {
    StorageException ex = new StorageException("err", new RuntimeException());
    ResponseEntity<ApiErrorResponse> resp = handler.handleStorageException(ex, request);
    assertThat(resp.getStatusCodeValue()).isEqualTo(500);
    assertThat(resp.getBody().status()).isEqualTo(500);
  }

  @Test
  void handleValidationExceptions_buildsBadRequest() {
    Exception ex = new ConstraintViolationException("violation", null);
    ResponseEntity<ApiErrorResponse> resp = handler.handleValidationExceptions(ex, request);
    assertThat(resp.getStatusCodeValue()).isEqualTo(400);
  }

  @Test
  void handleUploadSizeExceeded_buildsPayloadTooLarge() {
    MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1);
    ResponseEntity<ApiErrorResponse> resp = handler.handleUploadSizeExceeded(ex, request);
    assertThat(resp.getStatusCodeValue()).isEqualTo(413);
  }

  @Test
  void handleGenericException_buildsServerError() {
    Exception ex = new RuntimeException("oops");
    ResponseEntity<ApiErrorResponse> resp = handler.handleGenericException(ex, request);
    assertThat(resp.getStatusCodeValue()).isEqualTo(500);
  }
}
