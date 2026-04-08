package com.sophon.server.infrastructure.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.nio.file.FileSystems.getDefault;
import static java.nio.file.Files.isDirectory;

/**
 * Watches {@code config/server.yaml} and triggers debounced reloads.
 * Bind address/port changes are logged; full effect may require process restart.
 */
@Component
@ConditionalOnProperty(prefix = "sophon.config.hot-reload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ServerConfigWatcher {

    private static final Logger log = LoggerFactory.getLogger(ServerConfigWatcher.class);

    private final SophonProperties sophonProperties;
    private final SophonRuntimeConfigHolder holder;

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean stopped;
    private final ScheduledExecutorService debounceScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sophon-config-debounce");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pendingReload;
    private String lastPortLog;
    private String lastHostLog;

    public ServerConfigWatcher(SophonProperties sophonProperties, SophonRuntimeConfigHolder holder) {
        this.sophonProperties = sophonProperties;
        this.holder = holder;
    }

    @Order(100)
    @EventListener(ContextRefreshedEvent.class)
    public void startOnce() {
        if (watchService != null) {
            return;
        }
        Path configDir = Path.of(sophonProperties.getHome(), "config");
        try {
            if (!isDirectory(configDir)) {
                return;
            }
            this.watchService = getDefault().newWatchService();
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            this.watchThread = new Thread(this::runLoop, "sophon-config-watch");
            this.watchThread.setDaemon(true);
            this.watchThread.start();
            log.info("Watching {} for server.yaml changes", configDir);
        } catch (IOException e) {
            log.warn("Could not start config watcher: {}", e.toString());
        }
    }

    private void runLoop() {
        while (!stopped && watchService != null) {
            try {
                WatchKey key = watchService.take();
                for (var ev : key.pollEvents()) {
                    Object ctx = ev.context();
                    if (ctx != null && "server.yaml".equals(ctx.toString())) {
                        scheduleReload();
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void scheduleReload() {
        long ms = sophonProperties.getHotReload().getDebounceMs();
        synchronized (debounceScheduler) {
            if (pendingReload != null) {
                pendingReload.cancel(false);
            }
            pendingReload = debounceScheduler.schedule(this::reloadQuietly, ms, TimeUnit.MILLISECONDS);
        }
    }

    private void reloadQuietly() {
        try {
            SophonRuntimeConfig before = holder.config();
            LoadedServerConfig loaded = holder.reloadFromDisk();
            SophonRuntimeConfig after = loaded.config();
            log.info("Hot-reloaded server.yaml (revision {})", loaded.revision());
            maybeLogBindMismatch(before, after);
        } catch (Exception e) {
            log.warn("Hot reload failed: {}", e.toString());
        }
    }

    private void maybeLogBindMismatch(SophonRuntimeConfig before, SophonRuntimeConfig after) {
        String p0 = String.valueOf(before.getServer().getPort());
        String p1 = String.valueOf(after.getServer().getPort());
        String h0 = String.valueOf(before.getServer().getHost());
        String h1 = String.valueOf(after.getServer().getHost());
        if (!p0.equals(p1) || !h0.equals(h1)) {
            if (!p0.equals(p1) && !p1.equals(lastPortLog)) {
                lastPortLog = p1;
                log.warn("server.port changed in YAML; restart the process for bind port to take effect");
            }
            if (!h0.equals(h1) && !h1.equals(lastHostLog)) {
                lastHostLog = h1;
                log.warn("server.host changed in YAML; restart the process for bind address to take effect");
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        stopped = true;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        debounceScheduler.shutdownNow();
    }
}
