package com.sophon.core.llm;

import com.sophon.core.llm.providers.DashscopeProvider;
import com.sophon.core.llm.providers.MockProvider;

import java.util.HashMap;
import java.util.Map;

public class LLMProviderFactory {
    private final Map<String, LLMProvider> providers = new HashMap<>();

    public LLMProviderFactory() {
        providers.put("mock", new MockProvider());
        providers.put("dashscope", new DashscopeProvider(System.getenv("DASHSCOPE_API_KEY")));
    }

    public LLMProvider get(String name) {
        LLMProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("未知 LLM provider: " + name + "。可用: " + providers.keySet());
        }
        return provider;
    }

    public LLMProvider getDefault() {
        String dashKey = System.getenv("DASHSCOPE_API_KEY");
        if (dashKey != null && !dashKey.isBlank()) {
            return providers.getOrDefault("dashscope", providers.get("mock"));
        }
        return providers.get("mock");
    }
}
