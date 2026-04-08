package com.sophon.server.core.tool;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class ToolService {

    private final ToolSandboxService sandboxService;

    public ToolService(ToolSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    public Object execute(String toolName, Map<String, Object> input) {
        return sandboxService.execute(toolName, () -> {
            if ("echo".equals(toolName)) {
                return Map.of("echo", input == null ? Map.of() : input);
            }
            if ("time".equals(toolName)) {
                return Map.of("now", Instant.now().toString());
            }
            throw new IllegalArgumentException("unknown tool: " + toolName);
        });
    }
}
