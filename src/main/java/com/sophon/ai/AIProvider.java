package com.sophon.ai;

import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.tool.ToolDefinition;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 模型供应商端口；阶段 2 要求<strong>同步</strong>补全，<strong>阶段 4</strong> 增加流式片段（{@link #completeStreaming}）。
 */
public interface AIProvider {

    /**
     * 基于多轮消息生成助手回复（同步阻塞至返回或失败）。
     * 未向模型提供 {@code tools} 时，若模型仍返回 tool_calls，将抛出 {@link AIException}。
     */
    default String complete(List<LlmMessage> messages) throws AIException {
        CompletionResult r = complete(messages, List.of());
        if (r instanceof CompletionResult.Text t) {
            return t.text();
        }
        throw new AIException("模型返回了 tool_calls，但未提供工具定义");
    }

    /**
     * 同步补全；{@code tools} 为空时不向模型声明工具，期望得到纯文本。
     */
    CompletionResult complete(List<LlmMessage> messages, List<ToolDefinition> tools) throws AIException;

    /**
     * 流式：文本以 {@link StreamingChunk.TextToken} 下发；若模型在流末尾产生 tool_calls（SSE {@code delta.tool_calls}），
     * 在结束前发出 {@link StreamingChunk.ToolCallsFinished}。与同步路径一样传入 {@code tools}。
     */
    default Flux<StreamingChunk> completeStreaming(List<LlmMessage> messages) {
        return completeStreaming(messages, List.of());
    }

    Flux<StreamingChunk> completeStreaming(List<LlmMessage> messages, List<ToolDefinition> tools);
}
