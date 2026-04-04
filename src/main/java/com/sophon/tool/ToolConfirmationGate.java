package com.sophon.tool;

/**
 * 高危工具执行前的人工确认；CLI 可实现为 Console 询问，测试可注入恒 true/false。
 */
@FunctionalInterface
public interface ToolConfirmationGate {

    boolean confirm(String sessionId, String toolName, String argsSummary);
}
