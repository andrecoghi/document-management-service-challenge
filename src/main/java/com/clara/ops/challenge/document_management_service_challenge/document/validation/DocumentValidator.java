package com.clara.ops.challenge.document_management_service_challenge.document.validation;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Component
@Validated
public class DocumentValidator {

  public void validateUpload(MultipartFile file, PresignedUrlRequest request) {
    if (file == null || file.isEmpty()) {
      throw new InvalidRequestException("A non-empty PDF file must be provided");
    }
    if (file.getSize() > 500L * 1024L * 1024L) {
      throw new InvalidRequestException("File size exceeds the 500MB limit");
    }
    String contentType = file.getContentType();
    if (!StringUtils.hasText(contentType)) {
      contentType = MediaType.APPLICATION_PDF_VALUE;
    }
    if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)) {
      throw new InvalidRequestException("Only PDF documents are supported");
    }
    if (request == null) {
      throw new InvalidRequestException("Upload metadata is required");
    }
    if (!StringUtils.hasText(request.user())) {
      throw new InvalidRequestException("User is required");
    }
    if (!StringUtils.hasText(request.fileName())) {
      throw new InvalidRequestException("Document name is required");
    }
  }

  public void confirmUpload(ConfirmUploadRequest request) {
    if (request == null) {
      throw new InvalidRequestException("Confirm upload metadata is required");
    }
    if (!StringUtils.hasText(request.user())) {
      throw new InvalidRequestException("User is required");
    }
    if (!StringUtils.hasText(request.fileName())) {
      throw new InvalidRequestException("Document name is required");
    }
    if (!StringUtils.hasText(request.objectKey())) {
      throw new InvalidRequestException("Object key is required");
    }
    if (request.fileSize() <= 0) {
      throw new InvalidRequestException("File size must be positive");
    }
    if (!StringUtils.hasText(request.contentType())) {
      throw new InvalidRequestException("File type is required");
    }
  }
}
