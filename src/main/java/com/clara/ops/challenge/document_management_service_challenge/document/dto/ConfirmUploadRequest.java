package com.clara.ops.challenge.document_management_service_challenge.document.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

/** DTO for confirming an upload after the client has PUT the file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmUploadRequest(
    @NotBlank String user,
    @NotBlank String fileName,
    Set<@NotBlank String> tags,
    @NotBlank String objectKey,
    @Min(1) long fileSize,
    @NotBlank String contentType) {}
