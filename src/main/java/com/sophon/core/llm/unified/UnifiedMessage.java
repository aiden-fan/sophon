package com.sophon.core.llm.unified;

import java.util.List;

public record UnifiedMessage(
    UnifiedRole role,
    String content,
    String toolCallId,
    String toolName,
    List<ToolCall> toolCalls
) {
    public static UnifiedMessage system(String content) {
        return new UnifiedMessage(UnifiedRole.SYSTEM, content, null, null, List.of());
    }

    public static UnifiedMessage user(String content) {
        return new UnifiedMessage(UnifiedRole.USER, content, null, null, List.of());
    }

    public static UnifiedMessage assistant(String content) {
        return new UnifiedMessage(UnifiedRole.ASSISTANT, content, null, null, List.of());
    }

    public static UnifiedMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new UnifiedMessage(UnifiedRole.ASSISTANT, content, null, null, toolCalls);
    }

    public static UnifiedMessage tool(String content, String toolName, String toolCallId) {
        return new UnifiedMessage(UnifiedRole.TOOL, content, toolCallId, toolName, List.of());
    }
}
