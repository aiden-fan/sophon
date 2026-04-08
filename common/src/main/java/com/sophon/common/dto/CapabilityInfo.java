package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityInfo(
        String id,
        String type,
        String description
) {}
