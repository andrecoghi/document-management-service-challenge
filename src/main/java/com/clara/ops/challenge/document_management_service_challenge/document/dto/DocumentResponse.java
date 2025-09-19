package com.clara.ops.challenge.document_management_service_challenge.document.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String user,
    String name,
    Set<String> tags,
    long size,
    String type,
    Instant createdAt) {}
