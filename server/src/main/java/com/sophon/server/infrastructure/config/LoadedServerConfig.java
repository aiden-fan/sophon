package com.sophon.server.infrastructure.config;

import java.nio.file.Path;

/**
 * Snapshot of a load from {@code server.yaml}.
 */
public record LoadedServerConfig(
        SophonRuntimeConfig config,
        String revision,
        long loadedAtEpochMs,
        Path sourcePath
) {}
