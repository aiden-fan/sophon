package com.sophon.server.core.llm;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LLMService {

    private final Map<String, LLMProvider> providers;

    public LLMService(List<LLMProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toMap(LLMProvider::providerName, Function.identity()));
    }

    public Mono<String> generate(String provider, String model, List<LLMProvider.LLMMessage> messages) {
        LLMProvider p = providers.get(provider);
        if (p == null) {
            p = providers.getOrDefault("dashscope", providers.get("dashscope-mock"));
        }
        return p.generate(model == null ? "qwen-max" : model, messages);
    }

    public Flux<String> streamGenerate(String provider, String model, List<LLMProvider.LLMMessage> messages) {
        LLMProvider p = providers.get(provider);
        if (p == null) {
            p = providers.getOrDefault("dashscope", providers.get("dashscope-mock"));
        }
        return p.streamGenerate(model == null ? "qwen-max" : model, messages);
    }
}
