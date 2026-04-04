package com.sophon.ai.dto;

import java.util.List;

/**
 * 发往 {@link com.sophon.ai.AIProvider} 的单条消息（与供应商 API 的 role/content/tool_calls 对齐）。
 */
public record LlmMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public LlmMessage(String role, String content) {
        this(role, content, null, null);
    }

    public LlmMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role 不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("content 不能为 null");
        }
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static LlmMessage system(String text) {
        return new LlmMessage("system", text, null, null);
    }

    public static LlmMessage user(String text) {
        return new LlmMessage("user", text, null, null);
    }

    public static LlmMessage assistant(String text) {
        return new LlmMessage("assistant", text, null, null);
    }

    public static LlmMessage assistantWithToolCalls(List<ToolCall> calls) {
        List<ToolCall> copy = calls == null ? List.of() : List.copyOf(calls);
        return new LlmMessage("assistant", "", copy.isEmpty() ? null : copy, null);
    }

    public static LlmMessage tool(String callId, String toolContent) {
        return new LlmMessage("tool", toolContent, null, callId);
    }
}
