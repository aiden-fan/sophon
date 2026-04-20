package com.sophon.core.tool;

import java.util.Map;

public interface Tool {
    String name();
    String description();
    String parametersSchema();
    ToolResult execute(Map<String, Object> args);
}
