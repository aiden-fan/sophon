package com.sophon.core.llm.providers;

import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.unified.UnifiedChatRequest;
import com.sophon.core.llm.unified.UnifiedChatResponse;
import com.sophon.core.llm.unified.UnifiedStreamEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;

public class MockProvider implements LLMProvider {

    @Override
    public UnifiedChatResponse complete(UnifiedChatRequest request) {
        String lastUser = request.messages().stream()
            .filter(m -> m.role() == com.sophon.core.llm.unified.UnifiedRole.USER)
            .reduce((a, b) -> b)
            .map(m -> m.content())
            .orElse("");
        return new UnifiedChatResponse(
            "[Mock] 模拟回复: " + lastUser,
            List.of(),
            new UnifiedChatResponse.Usage(10, 20),
            "stop"
        );
    }

    @Override
    public Publisher<UnifiedStreamEvent> stream(UnifiedChatRequest request) {
        String response = "[Mock] 模拟流式回复: 这是自动生成的内容。";
        return subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                private boolean done = false;
                @Override public void request(long n) {
                    if (!done) {
                        for (int i = 0; i < response.length(); i++) {
                            subscriber.onNext(new UnifiedStreamEvent(String.valueOf(response.charAt(i)), false));
                        }
                        subscriber.onNext(UnifiedStreamEvent.finish());
                        subscriber.onComplete();
                        done = true;
                    }
                }
                @Override public void cancel() { done = true; }
            });
        };
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
