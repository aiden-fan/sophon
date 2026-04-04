package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiConfig {

    /**
     * 可为空；调用 Dashscope 时<strong>优先</strong>使用环境变量 {@code DASHSCOPE_API_KEY}（见 {@code DashscopeProvider}），
     * 未设置时再使用本字段。{@link ConfigManager#load()} 会将 {@code DASHSCOPE_API_KEY} 合并进配置，与运行时解析顺序一致。
     */
    @JsonProperty("api-key")
    private String apiKey = "";

    @JsonProperty("model")
    private String model = "qwen-turbo";

    /** 阶段 15：主模型失败时尝试的备用 model id（空表示不启用降级）。 */
    @JsonProperty("fallback-model")
    private String fallbackModel = "";

    /**
     * Dashscope OpenAI 兼容 Chat Completions 的 base，不含尾斜杠；
     * 完整请求路径为 {@code baseUrl + "/chat/completions"}。
     */
    @JsonProperty("base-url")
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @JsonProperty("timeout-seconds")
    private int timeoutSeconds = 120;

    @JsonProperty("system-prompt")
    private String systemPrompt = "You are a helpful assistant.";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model != null ? model : "qwen-turbo";
    }

    public String getFallbackModel() {
        return fallbackModel != null ? fallbackModel : "";
    }

    public void setFallbackModel(String fallbackModel) {
        this.fallbackModel = fallbackModel != null ? fallbackModel : "";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "";
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }
}
