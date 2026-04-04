package com.sophon.tool.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolResult;

/**
 * 示例工具：回显 {@code text} 字段，用于阶段 5 门禁与联调。
 */
public final class EchoLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolDefinition definition;

    public EchoLocalTool() {
        JsonNode schema;
        try {
            schema =
                    JSON.readTree(
                            """
                            {
                              "type": "object",
                              "properties": {
                                "text": {
                                  "type": "string",
                                  "description": "要回显的文本"
                                }
                              },
                              "required": ["text"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "echo",
                        "回显用户提供的 text 字符串，用于测试本地工具调用。",
                        schema);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws ToolExecutionException {
        JsonNode args;
        try {
            args = JSON.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("参数不是合法 JSON: " + argumentsJson, e);
        }
        String text = args.path("text").asText(null);
        if (text == null) {
            throw new ToolExecutionException("缺少 text 字段");
        }
        return ToolResult.ok("echo: " + text);
    }
}
