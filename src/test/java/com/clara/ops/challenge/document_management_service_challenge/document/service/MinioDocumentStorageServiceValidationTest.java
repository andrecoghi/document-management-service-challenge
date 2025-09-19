package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MinioDocumentStorageServiceValidationTest {

  private MinioClient minioClient;
  private DocumentStorageProperties properties;
  private MinioDocumentStorageService service;

  @BeforeEach
  void setUp() {
    minioClient = MinioClient.builder().endpoint("http://localhost").build();
    properties = new DocumentStorageProperties();
    service = new MinioDocumentStorageService(minioClient, properties);
  }

  @Test
  void upload_noBucketConfigured_throwsInvalidRequest() {
    properties.setBucket("");
    assertThatThrownBy(() -> service.upload("key", null, 0, "type"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No bucket configured for document storage");
  }

  @Test
  void generatePresignedGetUrl_noBucketConfigured_throwsInvalidRequest() {
    properties.setBucket(" ");
    assertThatThrownBy(() -> service.generatePresignedGetUrl("key"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No bucket configured for document storage");
  }
}
