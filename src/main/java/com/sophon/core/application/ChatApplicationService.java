package com.sophon.core.application;

import com.sophon.ai.AIException;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.core.agent.AgentEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

/**
 * 聊天用例：对外稳定入口；编排委托 {@link AgentEngine}。
 */
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final AgentEngine agentEngine;

    public ChatApplicationService(AgentEngine agentEngine) {
        this.agentEngine = agentEngine;
    }

    /**
     * 一轮对话：经 AgentEngine 写入用户消息并驱动模型（及未来 Tool 循环）。
     */
    public String chat(String sessionId, String userText) throws AIException {
        log.debug("ChatApplicationService.chat sessionId={}", sessionId);
        return agentEngine.runTurn(sessionId, userText);
    }

    /**
     * 流式对话：经 {@link AgentEngine#runTurnStreaming}；片段为 {@link StreamingChunk}（{@link StreamingChunk.TextToken} 可区分思考与正文）。
     */
    public Flux<StreamingChunk> chatStream(String sessionId, String userText) {
        log.debug("ChatApplicationService.chatStream sessionId={}", sessionId);
        return agentEngine.runTurnStreaming(sessionId, userText);
    }
}
