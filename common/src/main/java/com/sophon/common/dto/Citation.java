package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Citation(
        String sourceId,
        String title,
        String uri,
        String snippet
) {}
