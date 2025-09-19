package com.clara.ops.challenge.document_management_service_challenge.document.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** DTO for multipart upload metadata (used in /upload endpoint). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadDocumentRequest(
    @NotBlank @JsonProperty("user") String user,
    @NotBlank @JsonProperty("fileName") String fileName,
    @NotNull @Size(min = 0) @JsonProperty("tags") Set<@NotBlank String> tags) {}
