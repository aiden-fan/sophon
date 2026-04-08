package com.sophon.server.core.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LLMProvider {

    String providerName();

    Mono<String> generate(String model, List<LLMMessage> messages);

    Flux<String> streamGenerate(String model, List<LLMMessage> messages);

    record LLMMessage(String role, String content) {}
}
