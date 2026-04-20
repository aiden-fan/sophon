package com.sophon.core.llm.unified;

public record UnifiedStreamEvent(
    String delta,
    boolean isDone
) {
    public static UnifiedStreamEvent finish() {
        return new UnifiedStreamEvent("", true);
    }
}
