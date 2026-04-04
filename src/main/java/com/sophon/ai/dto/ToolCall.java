package com.sophon.ai.dto;

/**
 * 模型返回的一条 function 调用（OpenAI 兼容 {@code tool_calls} 项）。
 */
public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tool call id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name 不能为空");
        }
        if (argumentsJson == null) {
            throw new IllegalArgumentException("argumentsJson 不能为 null");
        }
    }
}
