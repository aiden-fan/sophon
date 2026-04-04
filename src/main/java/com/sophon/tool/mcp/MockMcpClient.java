package com.sophon.tool.mcp;

import java.util.concurrent.TimeoutException;

/** 进程内 Mock：成功路径或可控超时失败（单测与无 MCP 进程时的占位）。 */
public final class MockMcpClient implements McpClient {

    private final boolean timeout;

    public MockMcpClient() {
        this(false);
    }

    public MockMcpClient(boolean timeout) {
        this.timeout = timeout;
    }

    @Override
    public String invoke(String remoteToolName, String argumentsJson, int timeoutMillis) throws Exception {
        if (timeout) {
            throw new TimeoutException("MCP mock timeout");
        }
        return "{\"ok\":true,\"tool\":\"" + remoteToolName + "\",\"argsLen\":" + (argumentsJson == null ? 0 : argumentsJson.length()) + "}";
    }
}
