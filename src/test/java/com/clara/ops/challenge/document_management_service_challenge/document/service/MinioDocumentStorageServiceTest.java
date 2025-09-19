package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MinioDocumentStorageServiceTest {

  private MinioClient minioClient;
  private DocumentStorageProperties properties;
  private MinioDocumentStorageService storageService;

  @BeforeEach
  void setUp() {
    minioClient = mock(MinioClient.class);
    properties = new DocumentStorageProperties();
    properties.setBucket("bucket");
    storageService = new MinioDocumentStorageService(minioClient, properties);
  }

  @Test
  void uploadUploadsObjectWhenBucketExists() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

    byte[] data = "data".getBytes();
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    storageService.upload("key", in, data.length, "application/pdf");

    verify(minioClient).putObject(any(PutObjectArgs.class));
  }

  @Test
  void uploadCreatesBucketWhenNotExists() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false, true);
    doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));

    storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type");

    verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    verify(minioClient).putObject(any(PutObjectArgs.class));
  }

  @Test
  void uploadThrowsStorageExceptionWhenPutFails() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class)); // ensure no bucket error
    doThrow(new RuntimeException("fail")).when(minioClient).putObject(any(PutObjectArgs.class));

    assertThatThrownBy(
            () -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Failed to upload object");
  }

  @Test
  void generatePresignedGetUrlReturnsUrl() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://url");

    String url = storageService.generatePresignedGetUrl("key");
    assertThat(url).isEqualTo("http://url");
  }

  @Test
  void generatePresignedGetUrlThrowsStorageExceptionWhenStatFails() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    when(minioClient.statObject(any(StatObjectArgs.class)))
        .thenThrow(new RuntimeException("no object"));

    assertThatThrownBy(() -> storageService.generatePresignedGetUrl("key"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Failed to access object");
  }

  @Test
  void generatePresignedGetUrlThrowsStorageExceptionWhenUrlFails() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenThrow(new RuntimeException("fail-url"));

    assertThatThrownBy(() -> storageService.generatePresignedGetUrl("key"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Failed to generate download URL");
  }
}
