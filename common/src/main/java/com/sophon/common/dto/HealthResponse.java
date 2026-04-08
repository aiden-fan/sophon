package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * GET /api/v1/health payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResponse(
        String status,
        String version,
        Instant serverTime,
        String configRevision
) {}
