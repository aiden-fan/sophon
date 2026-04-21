package com.sophon.core.llm;

import com.sophon.config.AppConfig;
import com.sophon.core.llm.providers.DashscopeProvider;
import com.sophon.core.llm.providers.MockProvider;

import java.util.HashMap;
import java.util.Map;

public class LLMProviderFactory {
    private final AppConfig appConfig;
    private final Map<String, LLMProvider> providers = new HashMap<>();

    public LLMProviderFactory() {
        this(AppConfig.load());
    }

    public LLMProviderFactory(AppConfig appConfig) {
        this.appConfig = appConfig;
        providers.put("mock", new MockProvider());
        providers.put("dashscope", new DashscopeProvider(
            appConfig.ai().dashscopeApiKey(),
            appConfig.ai().dashscopeModel()
        ));
    }

    public LLMProvider get(String name) {
        LLMProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("未知 LLM provider: " + name + "。可用: " + providers.keySet());
        }
        return provider;
    }

    /**
     * 默认 Provider 选择顺序：
     * 1. 配置了 {@code sophon.ai.provider: dashscope} 且有 API Key → Dashscope
     * 2. 仅配置了 {@code provider: mock}，但环境变量或 yaml 里提供了非空 {@code DASHSCOPE_API_KEY} → 仍用 Dashscope（便于只设环境变量即可调用真实 API）
     * 3. 显式 {@code dashscope} 但无 Key → 回退 Mock，避免启动即报错
     * 4. 其余 → Mock
     */
    public LLMProvider getDefault() {
        String configured = appConfig.ai().normalizedProvider();
        String key = appConfig.ai().dashscopeApiKey();
        boolean hasKey = key != null && !key.isBlank();

        if ("dashscope".equals(configured)) {
            return hasKey ? providers.get("dashscope") : providers.get("mock");
        }
        // provider 为 mock（或未识别）时：只要 Key 存在就启用 Dashscope
        if (hasKey) {
            return providers.get("dashscope");
        }
        return providers.get("mock");
    }
}
