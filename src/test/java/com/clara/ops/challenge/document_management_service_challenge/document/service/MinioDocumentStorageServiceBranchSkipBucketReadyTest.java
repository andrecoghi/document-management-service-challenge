package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
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
class MinioDocumentStorageServiceBranchSkipBucketReadyTest {

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
  void uploadSkipsEnsureBucketWhenReady() throws Exception {
    when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

    storageService.upload("key1", in, 0, "type");
    storageService.upload("key2", in, 0, "type");

    verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
    verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
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
