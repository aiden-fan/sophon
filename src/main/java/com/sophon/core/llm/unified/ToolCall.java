package com.sophon.core.llm.unified;

import java.util.List;

public record ToolCall(
    String id,
    String name,
    String argsJson
) {
    public ToolCall(String name, String argsJson) {
        this(null, name, argsJson);
    }
}
