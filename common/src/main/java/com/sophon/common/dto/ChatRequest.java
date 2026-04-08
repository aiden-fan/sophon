package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String sessionId,
        String message,
        boolean stream,
        List<String> modalities,
        Integer ragTopK,
        Double ragMinScore
) {}
