package com.sophon.tool;

import java.util.Optional;

/**
 * 嵌套 {@link ToolExecutor#execute(String, com.sophon.ai.dto.ToolCall)} 时传递当前会话 id；
 * 供 {@link com.sophon.tool.local.InvokeSkillLocalTool} 等需会话上下文的工具使用。
 */
public final class ToolExecutionContext {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();

    private ToolExecutionContext() {}

    static void enter(String sessionId) {
        int d = DEPTH.get();
        if (sessionId != null && !sessionId.isBlank()) {
            SESSION_ID.set(sessionId);
        } else if (d == 0) {
            SESSION_ID.remove();
        }
        DEPTH.set(d + 1);
    }

    static void leave() {
        int d = DEPTH.get() - 1;
        if (d <= 0) {
            DEPTH.remove();
            SESSION_ID.remove();
        } else {
            DEPTH.set(d);
        }
    }

    public static Optional<String> currentSessionId() {
        String s = SESSION_ID.get();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}
