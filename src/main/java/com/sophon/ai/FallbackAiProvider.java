package com.sophon.ai;

import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.tool.ToolDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 阶段 15：主模型失败时回退到备用 {@link AIProvider}（通常为小模型或同端点不同 model）。
 */
public final class FallbackAiProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackAiProvider.class);

    private final AIProvider primary;
    private final AIProvider fallback;

    public FallbackAiProvider(AIProvider primary, AIProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public CompletionResult complete(List<LlmMessage> messages, List<ToolDefinition> tools) throws AIException {
        try {
            return primary.complete(messages, tools);
        } catch (AIException e) {
            log.warn("主模型调用失败，切换备用模型: {}", e.getMessage());
            return fallback.complete(messages, tools);
        }
    }

    @Override
    public Flux<StreamingChunk> completeStreaming(List<LlmMessage> messages, List<ToolDefinition> tools) {
        return primary
                .completeStreaming(messages, tools)
                .onErrorResume(
                        t -> {
                            if (t instanceof AIException) {
                                log.warn("主模型流式失败，切换备用模型: {}", t.getMessage());
                                return fallback.completeStreaming(messages, tools);
                            }
                            return Flux.error(t);
                        });
    }
}
