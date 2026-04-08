package com.sophon.server.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for effective runtime config; updated on startup and file reload.
 */
@Component
public class SophonRuntimeConfigHolder {

    private static final Logger log = LoggerFactory.getLogger(SophonRuntimeConfigHolder.class);

    private final SophonProperties sophonProperties;
    private final AtomicReference<LoadedServerConfig> ref = new AtomicReference<>();
    private final AtomicLong revisionSeq = new AtomicLong();

    public SophonRuntimeConfigHolder(SophonProperties sophonProperties) {
        this.sophonProperties = sophonProperties;
    }

    @PostConstruct
    void loadInitial() {
        reloadFromDisk();
    }

    public Path configFilePath() {
        return Path.of(sophonProperties.getHome(), "config", "server.yaml");
    }

    public LoadedServerConfig current() {
        LoadedServerConfig c = ref.get();
        if (c != null) {
            return c;
        }
        return reloadFromDisk();
    }

    /**
     * Reloads from disk; if the file is missing, applies an empty document (defaults).
     */
    public synchronized LoadedServerConfig reloadFromDisk() {
        Path path = configFilePath();
        try {
            Map<String, Object> root;
            if (!Files.isRegularFile(path)) {
                root = new LinkedHashMap<>();
                log.info("No server.yaml at {}; using embedded defaults for runtime config", path);
            } else {
                root = ServerYamlLoader.load(path);
            }
            SophonRuntimeConfig cfg = SophonRuntimeConfig.fromRootMap(root);
            String revision = "r-" + revisionSeq.incrementAndGet();
            long now = System.currentTimeMillis();
            LoadedServerConfig loaded = new LoadedServerConfig(cfg, revision, now, path);
            ref.set(loaded);
            log.debug("Loaded server config revision {}", revision);
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load server config from " + path, e);
        }
    }

    public SophonRuntimeConfig config() {
        return current().config();
    }
}
