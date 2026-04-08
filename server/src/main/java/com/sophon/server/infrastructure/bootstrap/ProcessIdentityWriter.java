package com.sophon.server.infrastructure.bootstrap;

import com.sophon.server.infrastructure.config.SophonProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@code ~/.sophon/data/server.pid} on startup and removes it on graceful shutdown.
 */
@Component
@Order(0)
public class ProcessIdentityWriter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessIdentityWriter.class);

    private final SophonProperties sophonProperties;
    private Path pidFile;

    public ProcessIdentityWriter(SophonProperties sophonProperties) {
        this.sophonProperties = sophonProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path dataDir = Path.of(sophonProperties.getHome(), "data");
        Path dbDir = dataDir.resolve("database");
        Path cacheDir = dataDir.resolve("cache");
        Files.createDirectories(dataDir);
        Files.createDirectories(dbDir);
        Files.createDirectories(cacheDir);
        this.pidFile = dataDir.resolve("server.pid");
        long pid = ProcessHandle.current().pid();
        Files.writeString(pidFile, String.valueOf(pid));
        log.info("Wrote PID {} to {}", pid, pidFile);
    }

    @PreDestroy
    public void removePid() {
        if (pidFile != null) {
            try {
                Files.deleteIfExists(pidFile);
            } catch (IOException e) {
                log.debug("Could not delete pid file: {}", e.toString());
            }
        }
    }
}
