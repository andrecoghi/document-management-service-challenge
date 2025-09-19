package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioDocumentStorageServiceTest {

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
  void uploadUploadsObjectWhenBucketExists() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

    byte[] data = "data".getBytes();
    storageService.upload("key", new ByteArrayInputStream(data), data.length, "application/pdf");

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
    doThrow(new RuntimeException("fail")).when(minioClient).putObject(any(PutObjectArgs.class));

    assertThatThrownBy(
            () -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Failed to upload object");
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
  void uploadThrowsInvalidRequestWhenNoBucketConfigured() {
    properties.setBucket("");

    assertThatThrownBy(
            () -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "type"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No bucket configured for document storage");
  }

  @Test
  void uploadSkipsEnsureBucketWhenReady() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

    storageService.upload("key1", new ByteArrayInputStream(new byte[0]), 0, "type");
    storageService.upload("key2", new ByteArrayInputStream(new byte[0]), 0, "type");

    verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
    verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
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
  void generatePresignedGetUrlThrowsInvalidRequestWhenNoBucketConfigured() {
    properties.setBucket("");

    assertThatThrownBy(() -> storageService.generatePresignedGetUrl("key"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No bucket configured for document storage");
  }

  @Test
  void generateUrlSkipsEnsureBucketWhenReady() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("url1", "url2");

    String first = storageService.generatePresignedGetUrl("obj1");
    String second = storageService.generatePresignedGetUrl("obj2");

    assertThat(first).isEqualTo("url1");
    assertThat(second).isEqualTo("url2");
    verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
  }

  @Test
  void getBucketReturnsConfiguredBucket() {
    assertThat(storageService.getBucket()).isEqualTo("bucket");
  }
}
