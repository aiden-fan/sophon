package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
        String id,
        String name,
        Instant createdAt,
        String llmProvider,
        String llmModel,
        List<String> tools
) {}
