package com.sophon.core.tool;

import com.sophon.core.llm.unified.UnifiedTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        Tool existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalStateException("工具已存在: " + tool.name());
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<UnifiedTool> listAll() {
        return tools.values().stream()
            .map(t -> new UnifiedTool(t.name(), t.description(), t.parametersSchema()))
            .toList();
    }

    public List<UnifiedTool> listByNames(List<String> names) {
        return names.stream()
            .map(tools::get)
            .filter(java.util.Objects::nonNull)
            .map(t -> new UnifiedTool(t.name(), t.description(), t.parametersSchema()))
            .toList();
    }

    public List<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    public void clear() {
        tools.clear();
    }
}
