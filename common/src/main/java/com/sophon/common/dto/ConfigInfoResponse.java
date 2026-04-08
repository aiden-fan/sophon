package com.sophon.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Safe snapshot of effective server config (no raw secrets).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfigInfoResponse(
        String revision,
        String sophonHome,
        String bindHost,
        int bindPort,
        boolean authenticationEnabled,
        String configPath,
        long lastLoadedAtEpochMs
) {}
