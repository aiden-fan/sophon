package com.sophon.tool;

/**
 * 进程内可执行工具；与 {@link ToolDefinition} 成对由 {@link ToolRegistry} 持有。
 */
public interface LocalTool {

    ToolDefinition definition();

    /**
     * @param argumentsJson 模型给出的 function.arguments JSON 字符串
     */
    ToolResult execute(String argumentsJson) throws ToolExecutionException;
}
