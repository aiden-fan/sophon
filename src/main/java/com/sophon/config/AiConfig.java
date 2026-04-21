package com.sophon.config;

public record AiConfig(
    String provider,
    String dashscopeApiKey,
    String dashscopeModel
) {
    public String normalizedProvider() {
        return provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase();
    }
}
