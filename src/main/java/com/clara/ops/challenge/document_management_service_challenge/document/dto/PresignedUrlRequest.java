package com.clara.ops.challenge.document_management_service_challenge.document.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PresignedUrlRequest(
    @NotBlank @JsonProperty("user") String user,
    @NotBlank @JsonProperty("fileName") String fileName,
    @NotNull @Size(min = 0) @JsonProperty("tags") Set<@NotBlank String> tags,
    @NotNull @Min(1) @JsonProperty("fileSize") Long fileSize) {

  /** Convenience constructor for tests and clients without explicit fileSize. */
  public PresignedUrlRequest(
      @NotBlank @JsonProperty("user") String user,
      @NotBlank @JsonProperty("fileName") String fileName,
      @NotNull @Size(min = 0) @JsonProperty("tags") Set<@NotBlank String> tags) {
    this(user, fileName, tags, 1L);
  }
}
