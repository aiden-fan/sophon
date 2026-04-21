package com.sophon.core.agent;

import com.sophon.ai.AIException;
import com.sophon.ai.AIProvider;
import com.sophon.ai.LlmRequestInspectable;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private final LlmCallFileLogger llmCallLogger;

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
        this.llmCallLogger =
                new LlmCallFileLogger(
                        java.nio.file.Path.of(System.getProperty("user.dir", ".")).resolve("log"));
    }

    /**
     * 处理一轮用户输入：写入用户消息，再按 {@link AgentConfig#getMaxRounds()} 执行 LLM（及 Tool 循环）。
     */
    public String runTurn(String sessionId, String userText) throws AIException {
        LlmCallFileLogger.TurnTrace trace = llmCallLogger.startTurn("sync", sessionId, userText);
        String lastReply = null;
        String error = null;
        sessionManager.getSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        conversationManager.appendUserMessage(sessionId, userText);
        int maxLlm = Math.max(1, agentConfig.getMaxRounds());
        int llmCalls = 0;
        List<ToolDefinition> defs = toolsForRequest(sessionId);
        try {
            while (llmCalls < maxLlm) {
                llmCalls++;
                List<LlmMessage> ctx = maybeAugmentRag(sessionId, conversationManager.buildMessagesForModel(sessionId));
                log.debug("AgentEngine LLM 调用 {}/{}，上下文条数={}，tools={}", llmCalls, maxLlm, ctx.size(), defs.size());
                String requestPayload = inspectRequestPayload(ctx, false, defs);
                CompletionResult result = aiProvider.complete(ctx, defs);
                llmCallLogger.appendSyncRound(
                        trace, llmCalls, ctx, defs, providerLabel(), requestPayload, result);
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
        } catch (AIException e) {
            error = e.getMessage();
            throw e;
        } catch (RuntimeException e) {
            error = e.getMessage();
            throw e;
        } finally {
            llmCallLogger.finishTurn(trace, lastReply, error);
        }
    }

    /**
     * 流式一轮：写入用户消息后，按 {@link AgentConfig#getMaxRounds()} 执行流式模型调用；文本片段带 {@link ModelOutputKind}（思考 / 正文）。
     */
    public Flux<StreamingChunk> runTurnStreaming(String sessionId, String userText) {
        LlmCallFileLogger.TurnTrace trace = llmCallLogger.startTurn("stream", sessionId, userText);
        sessionManager.getSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        conversationManager.appendUserMessage(sessionId, userText);
        int maxLlm = Math.max(1, agentConfig.getMaxRounds());
        AtomicReference<String> lastFinalReply = new AtomicReference<>("");
        AtomicBoolean finished = new AtomicBoolean(false);
        return streamLlm(sessionId, 0, maxLlm, trace, lastFinalReply)
                .doOnComplete(
                        () -> {
                            if (finished.compareAndSet(false, true)) {
                                llmCallLogger.finishTurn(trace, lastFinalReply.get(), null);
                            }
                        })
                .doOnError(
                        e -> {
                            if (finished.compareAndSet(false, true)) {
                                llmCallLogger.finishTurn(trace, lastFinalReply.get(), e.getMessage());
                            }
                        });
    }

    private Flux<StreamingChunk> streamLlm(
            String sessionId,
            int roundIndex,
            int maxLlm,
            LlmCallFileLogger.TurnTrace trace,
            AtomicReference<String> lastFinalReply) {
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
        AtomicBoolean roundLogged = new AtomicBoolean(false);
        Flux<StreamingChunk> modelFlux =
                aiProvider.completeStreaming(ctx, defs)
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
                                String requestPayload = inspectRequestPayload(ctx, true, defs);
                                llmCallLogger.appendStreamingRound(
                                        trace,
                                        roundIndex + 1,
                                        ctx,
                                        defs,
                                        providerLabel(),
                                        requestPayload,
                                        roundAccFinal.toString(),
                                        roundAccThinking.toString(),
                                        calls);
                                roundLogged.set(true);
                                persistAssistantStreamingRound(sessionId, roundAccFinal, roundAccThinking);
                                conversationManager.appendAssistantToolCallsMessage(sessionId, calls);
                                List<StreamingChunk> progress = executeToolCallsWithProgress(sessionId, calls);
                                return Flux.concat(
                                        Flux.just(
                                                new StreamingChunk.Progress(
                                                        "tool.plan", "模型请求调用 " + calls.size() + " 个工具")),
                                        Flux.fromIterable(progress),
                                        streamLlm(sessionId, roundIndex + 1, maxLlm, trace, lastFinalReply));
                            }
                            return Flux.empty();
                        })
                .doOnComplete(
                        () -> {
                            if (!roundLogged.get()) {
                                String requestPayload = inspectRequestPayload(ctx, true, defs);
                                llmCallLogger.appendStreamingRound(
                                        trace,
                                        roundIndex + 1,
                                        ctx,
                                        defs,
                                        providerLabel(),
                                        requestPayload,
                                        roundAccFinal.toString(),
                                        roundAccThinking.toString(),
                                        List.of());
                            }
                            if (roundAccFinal.length() > 0) {
                                lastFinalReply.set(roundAccFinal.toString());
                            }
                            persistAssistantStreamingRound(sessionId, roundAccFinal, roundAccThinking);
                        });
        return Flux.concat(
                Flux.just(
                        new StreamingChunk.Progress(
                                "llm.call",
                                "开始第 " + (roundIndex + 1) + " 轮模型调用（tools=" + defs.size() + "）")),
                modelFlux);
    }

    private List<StreamingChunk> executeToolCallsWithProgress(String sessionId, List<ToolCall> calls) {
        List<StreamingChunk> out = new ArrayList<>();
        for (ToolCall call : calls) {
            out.add(new StreamingChunk.Progress("tool.start", "执行工具: " + call.name()));
            ToolResult tr = toolExecutor.execute(sessionId, call);
            conversationManager.appendToolMessage(sessionId, call.id(), tr.content());
            out.add(
                    new StreamingChunk.Progress(
                            tr.success() ? "tool.done" : "tool.fail",
                            "工具 " + call.name() + (tr.success() ? " 执行完成" : " 执行失败")));
        }
        return out;
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

    private String inspectRequestPayload(List<LlmMessage> messages, boolean stream, List<ToolDefinition> tools) {
        if (aiProvider instanceof LlmRequestInspectable p) {
            try {
                return p.buildRequestPayload(messages, stream, tools);
            } catch (Exception e) {
                return "[inspect request payload failed] " + e.getMessage();
            }
        }
        return "[provider does not support request payload inspection]";
    }

    private String providerLabel() {
        if (aiProvider instanceof LlmRequestInspectable p) {
            return p.providerLabel();
        }
        return aiProvider.getClass().getSimpleName();
    }
}
