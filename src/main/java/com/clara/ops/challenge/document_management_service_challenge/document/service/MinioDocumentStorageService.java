package com.clara.ops.challenge.document_management_service_challenge.document.service;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioDocumentStorageService implements DocumentStorage {

  private final MinioClient minioClient;
  private final DocumentStorageProperties properties;
  private final AtomicBoolean bucketReady = new AtomicBoolean();

  @PostConstruct
  public void initBucket() {
    ensureBucket();
  }

  @Override
  public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
    ensureBucket();
    log.info(
        "[Minio Upload] Starting upload: objectKey={}, size={}, contentType={}",
        objectKey,
        size,
        contentType);
    var data = toByteArray(inputStream);
    int maxAttempts = 3;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        minioClient.putObject(
            PutObjectArgs.builder().bucket(getBucket()).object(objectKey).stream(
                    new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType)
                .build());
        log.info("[Minio Upload] Upload completed successfully: objectKey={}", objectKey);
        return;
      } catch (Exception e) {
        if (attempt == maxAttempts) {
          log.error(
              "[Minio Upload] Error uploading after {} attempts: objectKey={}, exceptionType={},"
                  + " message={}, cause={}",
              attempt,
              objectKey,
              e.getClass().getName(),
              e.getMessage(),
              e.getCause(),
              e);
          throw new StorageException("Failed to upload object to storage after retries", e);
        }
        log.warn("[Minio Upload] Attempt {}/{} failed, retrying...", attempt, maxAttempts, e);
        sleep(attempt);
      }
    }
  }

  @Override
  public String generatePresignedGetUrl(String objectKey) {
    ensureBucket();
    log.info("[Minio Download] Checking object: bucket={}, objectKey={}", getBucket(), objectKey);
    try {
      minioClient.statObject(
          StatObjectArgs.builder().bucket(getBucket()).object(objectKey).build());
    } catch (Exception e) {
      throw new StorageException("Failed to access object", e);
    }
    try {
      var url =
          minioClient.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(getBucket())
                  .object(objectKey)
                  .expiry(properties.getPresignedUrlExpirySeconds())
                  .build());
      var base = properties.getDownloadUrlBase();
      if (StringUtils.hasText(base)) {
        try {
          var uri = new URI(url);
          var path = uri.getRawPath();
          var query = uri.getRawQuery();
          return base + path + (query != null ? "?" + query : "");
        } catch (URISyntaxException ex) {
          log.warn("Invalid generated URL, falling back to original: {}", url, ex);
        }
      }
      return url;
    } catch (Exception e) {
      throw new StorageException("Failed to generate download URL", e);
    }
  }

  @Override
  public String generatePresignedPutUrl(String objectKey) {
    ensureBucket();
    try {
      return minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.PUT)
              .bucket(getBucket())
              .object(objectKey)
              .expiry(properties.getPresignedUrlExpirySeconds())
              .build());
    } catch (Exception e) {
      throw new StorageException("Failed to generate upload URL", e);
    }
  }

  @Override
  public String getBucket() {
    return properties.getBucket();
  }

  private void ensureBucket() {
    if (bucketReady.get()) {
      return;
    }
    synchronized (bucketReady) {
      if (bucketReady.get()) {
        return;
      }
      var bucketName = getBucket();
      if (!StringUtils.hasText(bucketName)) {
        throw new InvalidRequestException("No bucket configured for document storage");
      }
      try {
        var exists =
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
          minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
          log.info("Created bucket {}", bucketName);
        }
      } catch (Exception e) {
        log.error("Unable to ensure bucket existence for bucketName={}", bucketName, e);
        throw new StorageException("Unable to ensure bucket existence", e);
      }
      bucketReady.set(true);
    }
  }

  private byte[] toByteArray(InputStream inputStream) {
    try {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new StorageException("Failed to read input stream", e);
    }
  }

  private void sleep(int attempt) {
    try {
      Thread.sleep(1000L * attempt);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new StorageException("Upload retry interrupted", ie);
    }
  }
}
