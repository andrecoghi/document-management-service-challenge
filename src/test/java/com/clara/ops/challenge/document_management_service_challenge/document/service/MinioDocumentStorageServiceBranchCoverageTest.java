package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioDocumentStorageServiceBranchCoverageTest {

  @Mock private MinioClient minioClient;

  private DocumentStorageProperties properties;
  private MinioDocumentStorageService storageService;

  @BeforeEach
  void setUp() {
    properties = new DocumentStorageProperties();
    properties.setBucket("bucket");
    storageService = new MinioDocumentStorageService(minioClient, properties);
  }

  @Test
  void uploadThrowsStorageExceptionWhenBucketExistsCheckFails() throws Exception {
    doThrow(new RuntimeException("check failed"))
        .when(minioClient)
        .bucketExists(any(BucketExistsArgs.class));

    assertThatThrownBy(
            () -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Unable to ensure bucket existence");
  }

  @Test
  void uploadThrowsStorageExceptionWhenMakeBucketFails() throws Exception {
    doReturn(false).when(minioClient).bucketExists(any(BucketExistsArgs.class));
    doThrow(new RuntimeException("make failed"))
        .when(minioClient)
        .makeBucket(any(MakeBucketArgs.class));

    assertThatThrownBy(
            () -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Unable to ensure bucket existence");
  }

  @Test
  void generatePresignedGetUrlThrowsStorageExceptionWhenBucketExistsCheckFails() throws Exception {
    doThrow(new RuntimeException("check failed"))
        .when(minioClient)
        .bucketExists(any(BucketExistsArgs.class));

    assertThatThrownBy(() -> storageService.generatePresignedGetUrl("key"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Unable to ensure bucket existence");
  }

  @Test
  void generatePresignedGetUrlThrowsStorageExceptionWhenMakeBucketFails() throws Exception {
    doReturn(false).when(minioClient).bucketExists(any(BucketExistsArgs.class));
    doThrow(new RuntimeException("make failed"))
        .when(minioClient)
        .makeBucket(any(MakeBucketArgs.class));

    assertThatThrownBy(() -> storageService.generatePresignedGetUrl("key"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Unable to ensure bucket existence");
  }

  @Test
  void uploadThrowsInvalidRequestWhenNoBucketConfigured() {
    properties.setBucket("");
    assertThatThrownBy(
            () -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No bucket configured for document storage");
  }

  @Test
  void generatePresignedGetUrlThrowsInvalidRequestWhenNoBucketConfigured() {
    properties.setBucket("");
    assertThatThrownBy(() -> storageService.generatePresignedGetUrl("key"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No bucket configured for document storage");
  }
}
