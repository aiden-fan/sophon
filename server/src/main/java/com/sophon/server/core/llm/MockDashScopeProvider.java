package com.sophon.server.core.llm;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class MockDashScopeProvider implements LLMProvider {

    @Override
    public String providerName() {
        return "dashscope-mock";
    }

    @Override
    public Mono<String> generate(String model, List<LLMMessage> messages) {
        String prompt = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        String text = "[mock:" + model + "] " + prompt;
        return Mono.just(text);
    }

    @Override
    public Flux<String> streamGenerate(String model, List<LLMMessage> messages) {
        return generate(model, messages).flatMapMany(full -> {
            List<String> tokens = List.of(full.split("\\s+"));
            return Flux.fromIterable(tokens)
                    .map(t -> t + " ")
                    .delayElements(Duration.ofMillis(35))
                    .concatWith(Flux.just(""));
        });
    }
}
