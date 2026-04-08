package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error envelope for REST APIs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String error,
        String message,
        String path
) {}
