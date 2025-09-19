package com.clara.ops.challenge.document_management_service_challenge.document.service;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.config.MinioProperties;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MinioDocumentStorageService implements DocumentStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(MinioDocumentStorageService.class);

  private final MinioClient minioClient;
  private final DocumentStorageProperties properties;
  private final MinioProperties minioProperties;
  private final AtomicBoolean bucketReady = new AtomicBoolean();

  @Autowired
  public MinioDocumentStorageService(
      MinioClient minioClient,
      DocumentStorageProperties properties,
      MinioProperties minioProperties) {
    this.minioClient = minioClient;
    this.properties = properties;
    this.minioProperties = minioProperties;
  }

  /** Backward-compatible constructor for tests. */
  public MinioDocumentStorageService(
      MinioClient minioClient, DocumentStorageProperties properties) {
    this(minioClient, properties, new MinioProperties());
  }

  @PostConstruct
  public void initBucket() {
    ensureBucket();
  }

  @Override
  public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
    ensureBucket();
    LOGGER.info(
        "[Minio Upload] Starting upload: objectKey={}, size={}, contentType={}",
        objectKey,
        size,
        contentType);
    byte[] data;
    try {
      data = inputStream.readAllBytes();
    } catch (IOException e) {
      throw new StorageException("Failed to read input stream", e);
    }
    int maxAttempts = 3;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        minioClient.putObject(
            PutObjectArgs.builder().bucket(getBucket()).object(objectKey).stream(
                    new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType)
                .build());
        LOGGER.info("[Minio Upload] Upload completed successfully: objectKey={}", objectKey);
        return;
      } catch (Exception exception) {
        if (attempt == maxAttempts) {
          Throwable cause = exception.getCause();
          String causeType = (cause != null) ? cause.getClass().getName() : "null";
          String causeMsg = (cause != null) ? cause.getMessage() : "null";
          LOGGER.error(
              "[Minio Upload] Error uploading after {} attempts: objectKey={}, exceptionType={},"
                  + " message={}, causeType={}, causeMessage={}",
              attempt,
              objectKey,
              exception.getClass().getName(),
              exception.getMessage(),
              causeType,
              causeMsg,
              exception);
          throw new StorageException("Failed to upload object to storage after retries", exception);
        }
        LOGGER.warn(
            "[Minio Upload] Attempt {}/{} failed, retrying...", attempt, maxAttempts, exception);
        try {
          Thread.sleep(1000L * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new StorageException("Upload retry interrupted", ie);
        }
      }
    }
  }

  @Override
  public String generatePresignedGetUrl(String objectKey) {
    ensureBucket();
    LOGGER.info(
        "[Minio Download] Checking object: bucket={}, objectKey={}", getBucket(), objectKey);
    try {
      minioClient.statObject(
          StatObjectArgs.builder().bucket(getBucket()).object(objectKey).build());
    } catch (Exception e) {
      throw new StorageException("Failed to access object", e);
    }
    try {
      String url =
          minioClient.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(getBucket())
                  .object(objectKey)
                  .expiry(properties.getPresignedUrlExpirySeconds())
                  .build());
      String base = properties.getDownloadUrlBase();
      if (StringUtils.hasText(base)) {
        try {
          URI uri = new URI(url);
          String path = uri.getRawPath();
          String query = uri.getRawQuery();
          return base + path + (query != null ? "?" + query : "");
        } catch (URISyntaxException e) {
          LOGGER.warn("Invalid generated URL, falling back to original: {}", url, e);
          return url;
        }
      }
      return url;
    } catch (Exception exception) {
      throw new StorageException("Failed to generate download URL", exception);
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
    } catch (Exception exception) {
      throw new StorageException("Failed to generate upload URL", exception);
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
      String bucketName = getBucket();
      if (!StringUtils.hasText(bucketName)) {
        throw new InvalidRequestException("No bucket configured for document storage");
      }
      try {
        boolean exists =
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
          minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
          LOGGER.info("Created bucket {}", bucketName);
        }
      } catch (Exception exception) {
        LOGGER.error("Unable to ensure bucket existence for bucketName={}", bucketName, exception);
        throw new StorageException("Unable to ensure bucket existence", exception);
      }
      bucketReady.set(true);
    }
  }
}
