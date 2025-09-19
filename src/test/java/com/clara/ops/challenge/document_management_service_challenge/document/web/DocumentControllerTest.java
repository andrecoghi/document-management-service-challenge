package com.clara.ops.challenge.document_management_service_challenge.document.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clara.ops.challenge.document_management_service_challenge.document.dto.ConfirmUploadRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginatedDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PaginationMetadata;
import com.clara.ops.challenge.document_management_service_challenge.document.dto.PresignedUrlRequest;
import com.clara.ops.challenge.document_management_service_challenge.document.mapper.DocumentMapper;
import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.document.service.DocumentService;
import com.clara.ops.challenge.document_management_service_challenge.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentControllerTest {

  private static final String DOCUMENTS_BASE_PATH = "/document-management/documents";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private DocumentService documentService;
  @MockBean private DocumentMapper documentMapper;

  @Test
  void initiateUploadReturnsCreatedUrl() throws Exception {
    PresignedUrlRequest req = new PresignedUrlRequest("user", "Doc", Set.of(), 1L);
    when(documentService.getPresignedUrl(any())).thenReturn("http://minio/pre-signed");

    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH + "/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.url").value("http://minio/pre-signed"));
  }

  @Test
  void initiateUploadMissingMetadataReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post(DOCUMENTS_BASE_PATH + "/uploads").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchDocumentsReturnsPagedPayload() throws Exception {
    PaginatedDocumentResponse response =
        new PaginatedDocumentResponse(
            new PaginationMetadata(0, 20, 1, 1, 1),
            List.of(
                new DocumentResponse(
                    UUID.randomUUID(),
                    "user",
                    "Doc",
                    Set.of(),
                    0L,
                    "application/pdf",
                    java.time.Instant.now())));
    when(documentService.searchDocuments(any(), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(new DocumentEntity())));
    when(documentMapper.toPaginatedResponse(any())).thenReturn(response);

    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.currentPage").value(0));
  }

  @Test
  void searchDocumentsWithSortParams() throws Exception {
    PaginatedDocumentResponse response =
        new PaginatedDocumentResponse(new PaginationMetadata(0, 20, 1, 1, 1), List.of());
    when(documentService.searchDocuments(any(), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(new DocumentEntity())));
    when(documentMapper.toPaginatedResponse(any())).thenReturn(response);

    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH + "/search")
                .param("sort", "name,asc")
                .param("sort", "date,desc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  void searchDocumentsWithInvalidSortDirectionUsesDefault() throws Exception {
    PaginatedDocumentResponse response =
        new PaginatedDocumentResponse(new PaginationMetadata(0, 20, 1, 1, 1), List.of());
    when(documentService.searchDocuments(any(), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(new DocumentEntity())));
    when(documentMapper.toPaginatedResponse(any())).thenReturn(response);

    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH + "/search")
                .param("sort", "name,invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  void searchDocumentsInvalidParamsReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH + "/search?size=0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchDocumentsNegativePageReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH + "/search?page=-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void downloadDocumentReturnsPresignedUrl() throws Exception {
    UUID id = UUID.randomUUID();
    when(documentService.generateDownloadUrl(id)).thenReturn("http://example.com/download");

    mockMvc
        .perform(get(DOCUMENTS_BASE_PATH + "/" + id + "/download"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("http://example.com/download"));
  }

  @Test
  void downloadDocumentInvalidUUIDReturnsBadRequest() throws Exception {
    mockMvc
        .perform(get(DOCUMENTS_BASE_PATH + "/invalid-uuid/download"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirmUploadReturnsCreatedDocument() throws Exception {
    ConfirmUploadRequest req =
        new ConfirmUploadRequest(
            "user", "Doc", Set.of(), "user/test-uuid-doc.pdf", 1024L, "application/pdf");
    DocumentEntity entity = new DocumentEntity();
    entity.setUser("user");
    entity.setName("Doc");
    entity.setTags(Set.of());
    entity.setObjectKey("user/test-uuid-doc.pdf");
    entity.setSize(1024L);
    entity.setType("application/pdf");
    when(documentService.confirmUpload(any())).thenReturn(entity);
    DocumentResponse resp =
        new DocumentResponse(
            UUID.randomUUID(),
            "user",
            "Doc",
            Set.of(),
            1024L,
            "application/pdf",
            java.time.Instant.now());
    when(documentMapper.toResponse(any())).thenReturn(resp);
    mockMvc
        .perform(
            post(DOCUMENTS_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user").value("user"));
  }

  @Test
  void confirmUploadMissingMetadataReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post(DOCUMENTS_BASE_PATH).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }
}
