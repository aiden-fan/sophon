package com.sophon.observability;

import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolAuditLogger;

/** 将 Tool 审计写入 {@code tool_audit} 表。 */
public final class SqliteToolAuditLogger implements ToolAuditLogger {

    private final SQLiteStorage storage;

    public SqliteToolAuditLogger(SQLiteStorage storage) {
        this.storage = storage;
    }

    @Override
    public void record(String sessionId, String toolName, String argsSummary, boolean success) {
        storage.insertToolAudit(
                SQLiteStorage.newId(), sessionId, toolName, argsSummary, success, System.currentTimeMillis());
    }
}
