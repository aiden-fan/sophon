package com.sophon.server.core.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sophon.tool-sandbox")
public class ToolSandboxProperties {
    private long timeoutMs = 1500L;
    private List<String> allowList = new ArrayList<>(List.of("echo", "time"));
    private List<String> denyList = new ArrayList<>(List.of("shell", "exec"));

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public List<String> getAllowList() {
        return allowList;
    }

    public void setAllowList(List<String> allowList) {
        this.allowList = allowList;
    }

    public List<String> getDenyList() {
        return denyList;
    }

    public void setDenyList(List<String> denyList) {
        this.denyList = denyList;
    }
}
