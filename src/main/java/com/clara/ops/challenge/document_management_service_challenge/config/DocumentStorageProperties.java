package com.clara.ops.challenge.document_management_service_challenge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.storage")
public class DocumentStorageProperties {

  /** Bucket where documents are stored. */
  private String bucket;

  /** Optional prefix added before generated object keys. */
  private String prefix;

  /** Number of seconds the pre-signed download URLs remain valid. */
  private int presignedUrlExpirySeconds = 900;

  /** Optional external base URL to use for download links (e.g. https://mydomain.com). */
  private String downloadUrlBase;

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public int getPresignedUrlExpirySeconds() {
    return presignedUrlExpirySeconds;
  }

  public String getDownloadUrlBase() {
    return downloadUrlBase;
  }

  public void setDownloadUrlBase(String downloadUrlBase) {
    this.downloadUrlBase = downloadUrlBase;
  }

  public void setPresignedUrlExpirySeconds(int presignedUrlExpirySeconds) {
    this.presignedUrlExpirySeconds = presignedUrlExpirySeconds;
  }
}
