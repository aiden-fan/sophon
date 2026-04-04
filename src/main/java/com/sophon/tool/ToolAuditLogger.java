package com.sophon.tool;

/** Tool 调用审计（与业务日志分离）。 */
@FunctionalInterface
public interface ToolAuditLogger {

    void record(String sessionId, String toolName, String argsSummary, boolean success);
}
