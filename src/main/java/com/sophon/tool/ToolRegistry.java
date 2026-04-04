package com.sophon.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 进程内工具注册表；阶段 5 为全局单例，阶段 6 起可按会话过滤暴露给模型。
 */
public final class ToolRegistry {

    private final Map<String, LocalTool> tools = new LinkedHashMap<>();

    public void register(LocalTool tool) {
        ToolDefinition def = tool.definition();
        String name = def.getName();
        if (tools.containsKey(name)) {
            throw new IllegalStateException("工具已注册: " + name);
        }
        tools.put(name, tool);
    }

    public Optional<LocalTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolDefinition> definitions() {
        List<ToolDefinition> out = new ArrayList<>();
        for (LocalTool t : tools.values()) {
            out.add(t.definition());
        }
        return List.copyOf(out);
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    public static ToolRegistry empty() {
        return new ToolRegistry();
    }
}
