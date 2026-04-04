package com.sophon.observability;

import com.sophon.storage.sqlite.SQLiteStorage;

/** 将 LLM / Tool 用量写入 {@code usage_stats}。 */
public final class SqliteUsageTracker implements UsageTracker {

    private final SQLiteStorage storage;

    public SqliteUsageTracker(SQLiteStorage storage) {
        this.storage = storage;
    }

    @Override
    public void recordLlmUsage(String sessionId, String model, int promptTokens, int completionTokens) {
        storage.insertUsageStat(sessionId, model, promptTokens, completionTokens, System.currentTimeMillis());
    }

    @Override
    public void recordToolInvocation(String sessionId, String toolName) {
        storage.insertUsageStat(sessionId, "tool:" + toolName, 1, 0, System.currentTimeMillis());
    }
}
