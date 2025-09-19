package com.clara.ops.challenge.document_management_service_challenge.document.service;

import static org.mockito.Mockito.mock;

import com.clara.ops.challenge.document_management_service_challenge.config.DocumentStorageProperties;
import com.clara.ops.challenge.document_management_service_challenge.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;

class DocumentServiceValidationTest {

  private DocumentService documentService;

  @BeforeEach
  void setUp() {
    DocumentRepository mockRepo = mock(DocumentRepository.class);
    DocumentStorage mockStorage = mock(DocumentStorage.class);
    DocumentStorageProperties props = new DocumentStorageProperties();
    props.setBucket("bucket");
    props.setPrefix("");
    documentService = new DocumentService(mockRepo, mockStorage, props);
  }
}
