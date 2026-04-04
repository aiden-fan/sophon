package com.sophon.core.agent;

import com.sophon.ai.AIException;
import com.sophon.ai.AIProvider;
import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.ModelOutputKind;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.ai.dto.ToolCall;
import com.sophon.config.AgentConfig;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.knowledge.rag.RAGEngine;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话编排：LLM 与 Tool 循环；阶段 3 仅单轮 LLM；阶段 5 起支持本地工具。流式与同步共用 {@link #toolsForRequest(String)}。
 */
public class AgentEngine {

    private static final Logger log = LoggerFactory.getLogger(AgentEngine.class);

    private final SessionManager sessionManager;
    private final ConversationManager conversationManager;
    private final AIProvider aiProvider;
    private final AgentConfig agentConfig;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentToolSource agentToolSource;
    private final RAGEngine ragEngine;

    public AgentEngine(
            SessionManager sessionManager,
            ConversationManager conversationManager,
            AIProvider aiProvider,
            AgentConfig agentConfig) {
        this(sessionManager, conversationManager, aiProvider, agentConfig, ToolRegistry.empty(), null, null, null);
    }

    public AgentEngine(
            SessionManager sessionManager,
            ConversationManager conversationManager,
            AIProvider aiProvider,
            AgentConfig agentConfig,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor) {
        this(sessionManager, conversationManager, aiProvider, agentConfig, toolRegistry, toolExecutor, null, null);
    }

    public AgentEngine(
            SessionManager sessionManager,
            ConversationManager conversationManager,
            AIProvider aiProvider,
            AgentConfig agentConfig,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            AgentToolSource agentToolSource) {
        this(sessionManager, conversationManager, aiProvider, agentConfig, toolRegistry, toolExecutor, agentToolSource, null);
    }

    public AgentEngine(
            SessionManager sessionManager,
            ConversationManager conversationManager,
            AIProvider aiProvider,
            AgentConfig agentConfig,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            AgentToolSource agentToolSource,
            RAGEngine ragEngine) {
        this.sessionManager = sessionManager;
        this.conversationManager = conversationManager;
        this.aiProvider = aiProvider;
        this.agentConfig = agentConfig != null ? agentConfig : new AgentConfig();
        this.toolRegistry = toolRegistry != null ? toolRegistry : ToolRegistry.empty();
        this.toolExecutor =
                toolExecutor != null ? toolExecutor : new ToolExecutor(this.toolRegistry, sessionManager);
        this.agentToolSource = agentToolSource;
        this.ragEngine = ragEngine;
    }

    /**
     * 处理一轮用户输入：写入用户消息，再按 {@link AgentConfig#getMaxRounds()} 执行 LLM（及 Tool 循环）。
     */
    public String runTurn(String sessionId, String userText) throws AIException {
        sessionManager.getSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        conversationManager.appendUserMessage(sessionId, userText);
        int maxLlm = Math.max(1, agentConfig.getMaxRounds());
        String lastReply = null;
        int llmCalls = 0;
        List<ToolDefinition> defs = toolsForRequest(sessionId);
        while (llmCalls < maxLlm) {
            llmCalls++;
            List<LlmMessage> ctx = maybeAugmentRag(sessionId, conversationManager.buildMessagesForModel(sessionId));
            log.debug("AgentEngine LLM 调用 {}/{}，上下文条数={}，tools={}", llmCalls, maxLlm, ctx.size(), defs.size());
            CompletionResult result = aiProvider.complete(ctx, defs);
            if (result instanceof CompletionResult.Text t) {
                conversationManager.appendAssistantSegments(sessionId, t.content(), t.thinking());
                lastReply = t.text();
                if (lastReply.isBlank() && t.thinking() != null && !t.thinking().isBlank()) {
                    lastReply = t.thinking();
                }
                break;
            }
            if (result instanceof CompletionResult.ToolCalls tc) {
                conversationManager.appendAssistantToolCallsMessage(sessionId, tc.calls());
                for (ToolCall call : tc.calls()) {
                    ToolResult tr = toolExecutor.execute(sessionId, call);
                    conversationManager.appendToolMessage(sessionId, call.id(), tr.content());
                }
            }
        }
        if (lastReply == null) {
            throw new AIException(
                    "在 max-rounds=" + maxLlm + " 次模型调用内未获得最终文本回复（可能一直在请求工具）");
        }
        return lastReply;
    }

    /**
     * 流式一轮：写入用户消息后，按 {@link AgentConfig#getMaxRounds()} 执行流式模型调用；文本片段带 {@link ModelOutputKind}（思考 / 正文）。
     */
    public Flux<StreamingChunk> runTurnStreaming(String sessionId, String userText) {
        sessionManager.getSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        conversationManager.appendUserMessage(sessionId, userText);
        int maxLlm = Math.max(1, agentConfig.getMaxRounds());
        return streamLlm(sessionId, 0, maxLlm);
    }

    private Flux<StreamingChunk> streamLlm(String sessionId, int roundIndex, int maxLlm) {
        if (roundIndex >= maxLlm) {
            return Flux.error(
                    new AIException(
                            "在 max-rounds=" + maxLlm + " 次流式模型调用内未获得最终文本回复（可能一直在请求工具）"));
        }
        List<LlmMessage> ctx = maybeAugmentRag(sessionId, conversationManager.buildMessagesForModel(sessionId));
        List<ToolDefinition> defs = toolsForRequest(sessionId);
        log.debug(
                "AgentEngine 流式 LLM {}/{}，上下文条数={}，tools={}",
                roundIndex + 1,
                maxLlm,
                ctx.size(),
                defs.size());
        StringBuilder roundAccFinal = new StringBuilder();
        StringBuilder roundAccThinking = new StringBuilder();
        return aiProvider
                .completeStreaming(ctx, defs)
                .concatMap(
                        chunk -> {
                            if (chunk instanceof StreamingChunk.TextToken tt) {
                                if (tt.kind() == ModelOutputKind.THINKING) {
                                    roundAccThinking.append(tt.text());
                                } else {
                                    roundAccFinal.append(tt.text());
                                }
                                return Flux.just(chunk);
                            }
                            if (chunk instanceof StreamingChunk.ToolCallsFinished finished) {
                                List<ToolCall> calls = finished.calls();
                                persistAssistantStreamingRound(sessionId, roundAccFinal, roundAccThinking);
                                conversationManager.appendAssistantToolCallsMessage(sessionId, calls);
                                for (ToolCall call : calls) {
                                    ToolResult tr = toolExecutor.execute(sessionId, call);
                                    conversationManager.appendToolMessage(sessionId, call.id(), tr.content());
                                }
                                return streamLlm(sessionId, roundIndex + 1, maxLlm);
                            }
                            return Flux.empty();
                        })
                .doOnComplete(
                        () -> persistAssistantStreamingRound(sessionId, roundAccFinal, roundAccThinking));
    }

    private void persistAssistantStreamingRound(
            String sessionId, StringBuilder roundAccFinal, StringBuilder roundAccThinking) {
        if (roundAccFinal.length() == 0 && roundAccThinking.length() == 0) {
            return;
        }
        String thinking = roundAccThinking.length() == 0 ? null : roundAccThinking.toString();
        conversationManager.appendAssistantSegments(sessionId, roundAccFinal.toString(), thinking);
        roundAccFinal.setLength(0);
        roundAccThinking.setLength(0);
    }

    private List<LlmMessage> maybeAugmentRag(String sessionId, List<LlmMessage> ctx) {
        if (ragEngine == null) {
            return ctx;
        }
        return ragEngine.augmentForChat(sessionId, ctx);
    }

    private List<ToolDefinition> toolsForRequest(String sessionId) {
        if (!agentConfig.isToolsEnabled()) {
            return List.of();
        }
        SessionCapabilityConfig cap = sessionManager.getSession(sessionId).orElseThrow().getCapabilities();
        List<ToolDefinition> defs = new ArrayList<>();
        for (ToolDefinition d : toolRegistry.definitions()) {
            if (cap.allowsTool(d.getName())) {
                defs.add(d);
            }
        }
        if (agentToolSource != null) {
            for (ToolDefinition d : agentToolSource.extraToolDefinitions(sessionId)) {
                if (cap.allowsTool(d.getName())) {
                    defs.add(d);
                }
            }
        }
        if (defs.isEmpty()) {
            return List.of();
        }
        return List.copyOf(defs);
    }
}
