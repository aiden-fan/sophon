package com.sophon.observability;

/** 按会话/模型记录近似 token 用量（阶段 13）；Tool 调用可记为伪用量。 */
public interface UsageTracker {

    void recordLlmUsage(String sessionId, String model, int promptTokens, int completionTokens);

    /** 默认空实现；SQLite 实现可写入 {@code usage_stats}。 */
    default void recordToolInvocation(String sessionId, String toolName) {}
}
