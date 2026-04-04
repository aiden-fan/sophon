package com.sophon.tool.mcp;

/**
 * MCP 远程工具调用的最小端口（阶段 11）；实现可为进程内 Mock 或后续 stdio/HTTP 客户端。
 */
public interface McpClient {

    /**
     * @param remoteToolName 远端工具名
     * @param argumentsJson  JSON 参数
     * @param timeoutMillis  超时毫秒
     * @return 供应商返回的 JSON 文本
     */
    String invoke(String remoteToolName, String argumentsJson, int timeoutMillis) throws Exception;
}
