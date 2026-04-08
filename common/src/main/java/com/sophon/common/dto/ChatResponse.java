package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String sessionId,
        String messageId,
        String content,
        List<Citation> citations
) {}
