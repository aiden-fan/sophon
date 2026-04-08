package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * POST /api/v1/sessions body (subset aligned with docs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionCreateRequest(
        String name,
        Map<String, Object> llm,
        List<String> tools
) {}
