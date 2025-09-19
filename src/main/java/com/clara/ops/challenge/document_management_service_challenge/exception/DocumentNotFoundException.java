package com.clara.ops.challenge.document_management_service_challenge.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

  public DocumentNotFoundException(UUID documentId) {
    super("Document %s was not found".formatted(documentId));
  }
}
