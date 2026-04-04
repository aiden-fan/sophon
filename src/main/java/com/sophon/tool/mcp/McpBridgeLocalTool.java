package com.sophon.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolRisk;
import com.sophon.tool.ToolResult;

/**
 * 将 MCP 调用暴露为本地 Tool（默认名 {@code mcp_demo}），与会话级禁用策略一致。
 */
public final class McpBridgeLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolDefinition definition;
    private final McpClient client;
    private final int defaultTimeoutMs;

    public McpBridgeLocalTool(McpClient client, int defaultTimeoutMs) {
        this.client = client;
        this.defaultTimeoutMs = Math.max(100, defaultTimeoutMs);
        JsonNode schema;
        try {
            schema =
                    JSON.readTree(
                            """
                            {
                              "type": "object",
                              "properties": {
                                "remote_tool": {"type": "string", "description": "MCP 侧工具名"},
                                "payload": {"type": "string", "description": "JSON 字符串参数"}
                              },
                              "required": ["remote_tool"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "mcp_demo",
                        "经 MCP 客户端调用远端工具（Mock 或真实实现）；参数 remote_tool + 可选 payload。",
                        schema,
                        ToolRisk.STANDARD);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws ToolExecutionException {
        JsonNode root;
        try {
            root = JSON.readTree(argumentsJson == null ? "{}" : argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("非法 JSON", e);
        }
        String remote = root.path("remote_tool").asText("").trim();
        if (remote.isEmpty()) {
            throw new ToolExecutionException("需要 remote_tool");
        }
        String payload = root.path("payload").asText("{}");
        try {
            String out = client.invoke(remote, payload, defaultTimeoutMs);
            return ToolResult.ok(out);
        } catch (Exception e) {
            return ToolResult.error("MCP 调用失败: " + e.getMessage());
        }
    }
}
