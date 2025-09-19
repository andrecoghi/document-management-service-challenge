package com.clara.ops.challenge.document_management_service_challenge.document.dto;

import java.util.List;

public record PaginatedDocumentResponse(
    PaginationMetadata metadata, List<DocumentResponse> documents) {}
