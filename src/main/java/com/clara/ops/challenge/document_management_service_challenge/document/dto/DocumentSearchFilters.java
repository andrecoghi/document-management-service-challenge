package com.clara.ops.challenge.document_management_service_challenge.document.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentSearchFilters(String user, String fileName, Set<String> tags) {}
