package com.sophon.core.llm;

import com.sophon.core.llm.unified.UnifiedChatRequest;
import com.sophon.core.llm.unified.UnifiedChatResponse;
import com.sophon.core.llm.unified.UnifiedStreamEvent;
import org.reactivestreams.Publisher;

public interface LLMProvider {

    UnifiedChatResponse complete(UnifiedChatRequest request);

    Publisher<UnifiedStreamEvent> stream(UnifiedChatRequest request);

    String providerName();
}
