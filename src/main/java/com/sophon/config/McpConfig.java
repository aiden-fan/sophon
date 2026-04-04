package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/** MCP 客户端参数（阶段 11）；当前默认 Mock 实现仍消费超时配置。 */
public class McpConfig {

    @JsonProperty("timeout-seconds")
    private int timeoutSeconds = 30;

    @JsonProperty("enabled")
    private boolean enabled = true;

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int timeoutMillis() {
        return getTimeoutSeconds() * 1000;
    }
}
