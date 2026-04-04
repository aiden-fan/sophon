package com.sophon.tool;

/**
 * 本地工具执行结果，序列化为发给模型的 {@code tool} 消息 {@code content}。
 */
public record ToolResult(boolean success, String content) {

    public ToolResult {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为 null");
        }
    }

    public static ToolResult ok(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message);
    }
}
