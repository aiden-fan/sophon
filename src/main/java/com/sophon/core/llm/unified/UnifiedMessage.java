package com.sophon.core.llm.unified;

import java.util.List;

public record UnifiedMessage(
    UnifiedRole role,
    String content
) {
    public static UnifiedMessage system(String content) {
        return new UnifiedMessage(UnifiedRole.SYSTEM, content);
    }

    public static UnifiedMessage user(String content) {
        return new UnifiedMessage(UnifiedRole.USER, content);
    }

    public static UnifiedMessage assistant(String content) {
        return new UnifiedMessage(UnifiedRole.ASSISTANT, content);
    }
}
