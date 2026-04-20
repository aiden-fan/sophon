package com.sophon.core.llm.unified;

import java.util.List;

public record UnifiedChatResponse(
    String content,
    List<ToolCall> toolCalls,
    Usage usage,
    String finishReason
) {
    public record Usage(int promptTokens, int completionTokens) { }

    public static UnifiedChatResponse textOnly(String content) {
        return new UnifiedChatResponse(content, List.of(), null, "stop");
    }
}
