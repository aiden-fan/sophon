package com.sophon.server.core.tool;

import com.sophon.server.infrastructure.store.ToolAuditRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class ToolSandboxService {

    private final ToolSandboxProperties properties;
    private final ToolAuditRepository toolAuditRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ToolSandboxService(ToolSandboxProperties properties, ToolAuditRepository toolAuditRepository) {
        this.properties = properties;
        this.toolAuditRepository = toolAuditRepository;
    }

    public <T> T execute(String toolName, Supplier<T> supplier) {
        Set<String> deny = new HashSet<>(properties.getDenyList());
        Set<String> allow = new HashSet<>(properties.getAllowList());
        if (deny.contains(toolName)) {
            toolAuditRepository.log(toolName, false, "tool is in deny-list", 0L);
            throw new IllegalArgumentException("tool denied by sandbox policy");
        }
        if (!allow.contains(toolName)) {
            toolAuditRepository.log(toolName, false, "tool is not in allow-list", 0L);
            throw new IllegalArgumentException("tool not allowed by sandbox policy");
        }
        long start = System.nanoTime();
        Future<T> f = executor.submit(supplier::get);
        try {
            T out = f.get(Math.max(100L, properties.getTimeoutMs()), TimeUnit.MILLISECONDS);
            long ms = (System.nanoTime() - start) / 1_000_000;
            toolAuditRepository.log(toolName, true, "ok", ms);
            return out;
        } catch (TimeoutException e) {
            f.cancel(true);
            toolAuditRepository.log(toolName, false, "timeout", properties.getTimeoutMs());
            throw new IllegalArgumentException("tool execution timeout");
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            toolAuditRepository.log(toolName, false, e.getClass().getSimpleName(), ms);
            throw new IllegalArgumentException("tool execution failed: " + e.getMessage());
        }
    }
}
