package com.sophon.cli;

import com.sophon.tool.ToolConfirmationGate;

import java.io.Console;

/**
 * 终端高危确认：无 {@link System#console()} 时拒绝执行（避免无人值守环境误跑高危 Tool）。
 */
public final class ConsoleToolConfirmationGate implements ToolConfirmationGate {

    @Override
    public boolean confirm(String sessionId, String toolName, String argsSummary) {
        Console c = System.console();
        if (c == null) {
            return false;
        }
        c.printf(
                "%n[高危工具] %s | 会话=%s%n参数摘要: %s%n确认执行请输入 y 后回车，其它键取消: ",
                toolName, sessionId, argsSummary);
        String line = c.readLine();
        return line != null && "y".equalsIgnoreCase(line.trim());
    }
}
