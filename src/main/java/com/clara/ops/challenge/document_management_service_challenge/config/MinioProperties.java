package com.clara.ops.challenge.document_management_service_challenge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

  /** MinIO/S3 endpoint, e.g. http://localhost:9000. */
  private String endpoint;

  /** Access key used to authenticate against MinIO. */
  private String accessKey;

  /** Secret key used to authenticate against MinIO. */
  private String secretKey;

  /** Whether the endpoint should be accessed using HTTPS. */
  private boolean secure;

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public boolean isSecure() {
    return secure;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }
}
