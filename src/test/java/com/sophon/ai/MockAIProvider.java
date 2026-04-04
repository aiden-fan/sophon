package com.sophon.ai;

import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.tool.ToolDefinition;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** 测试用：可注入回复策略。 */
public class MockAIProvider implements AIProvider {

    private final BiFunction<List<LlmMessage>, List<ToolDefinition>, CompletionResult> reply2;

    public MockAIProvider(Function<List<LlmMessage>, String> reply) {
        this.reply2 = (msgs, tools) -> new CompletionResult.Text(reply.apply(msgs));
    }

    public MockAIProvider(BiFunction<List<LlmMessage>, List<ToolDefinition>, CompletionResult> reply2) {
        this.reply2 = reply2;
    }

    @Override
    public CompletionResult complete(List<LlmMessage> messages, List<ToolDefinition> tools) throws AIException {
        return reply2.apply(messages, tools);
    }

    @Override
    public Flux<StreamingChunk> completeStreaming(List<LlmMessage> messages, List<ToolDefinition> tools) {
        try {
            CompletionResult r = complete(messages, tools);
            if (r instanceof CompletionResult.Text t) {
                return splitToTextTokens(t.text());
            }
            if (r instanceof CompletionResult.ToolCalls tc) {
                return Flux.just(new StreamingChunk.ToolCallsFinished(tc.calls()));
            }
            return Flux.empty();
        } catch (RuntimeException e) {
            return Flux.error(e);
        } catch (AIException e) {
            return Flux.error(e);
        }
    }

    private static Flux<StreamingChunk> splitToTextTokens(String s) {
        if (s == null || s.isEmpty()) {
            return Flux.empty();
        }
        List<StreamingChunk> chunks = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            chunks.add(new StreamingChunk.TextToken(new String(Character.toChars(cp))));
            i += Character.charCount(cp);
        }
        return Flux.fromIterable(chunks);
    }
}
