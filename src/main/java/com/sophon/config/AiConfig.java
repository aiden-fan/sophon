package com.sophon.config;

public record AiConfig(
    String provider,
    String dashscopeApiKey,
    String dashscopeModel,
    /** 单次 Dashscope chat/completions 请求超时（秒），长章节生成建议 ≥ 120 */
    int dashscopeRequestTimeoutSeconds
) {
    public String normalizedProvider() {
        return provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase();
    }
}
