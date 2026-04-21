package com.sophon.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.ToolCall;
import com.sophon.tool.ToolDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 将一次“用户输入到最终完成”期间的 LLM 请求与响应聚合到单个文件，落盘到 ./log 目录。
 */
public final class LlmCallFileLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmCallFileLogger.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path logDir;

    public record TurnTrace(
            String turnId,
            String mode,
            String sessionId,
            String userInput,
            Instant startedAt,
            ArrayNode rounds) {}

    public LlmCallFileLogger(Path logDir) {
        this.logDir = logDir;
    }

    public TurnTrace startTurn(String mode, String sessionId, String userInput) {
        return new TurnTrace(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                mode,
                sessionId != null ? sessionId : "",
                userInput != null ? userInput : "",
                Instant.now(),
                JSON.createArrayNode());
    }

    public void appendSyncRound(
            TurnTrace turn,
            int round,
            List<LlmMessage> requestMessages,
            List<ToolDefinition> tools,
            String provider,
            String requestPayload,
            CompletionResult result) {
        ObjectNode n = turn.rounds().addObject();
        n.put("round", round);
        n.put("mode", "sync");
        n.put("provider", provider != null ? provider : "");
        putRequestPayload(n, requestPayload);
        n.set("request_messages", toMessagesArray(requestMessages));
        n.set("tools", toToolNames(tools));
        ObjectNode resp = n.putObject("response");
        if (result instanceof CompletionResult.Text t) {
            resp.put("type", "text");
            resp.put("content", t.content() != null ? t.content() : "");
            if (t.thinking() != null) {
                resp.put("thinking", t.thinking());
            }
        } else if (result instanceof CompletionResult.ToolCalls tc) {
            resp.put("type", "tool_calls");
            resp.set("tool_calls", toToolCallsArray(tc.calls()));
        } else {
            resp.put("type", "unknown");
        }
    }

    public void appendStreamingRound(
            TurnTrace turn,
            int round,
            List<LlmMessage> requestMessages,
            List<ToolDefinition> tools,
            String provider,
            String requestPayload,
            String finalText,
            String thinkingText,
            List<ToolCall> toolCalls) {
        ObjectNode n = turn.rounds().addObject();
        n.put("round", round);
        n.put("mode", "stream");
        n.put("provider", provider != null ? provider : "");
        putRequestPayload(n, requestPayload);
        n.set("request_messages", toMessagesArray(requestMessages));
        n.set("tools", toToolNames(tools));
        ObjectNode resp = n.putObject("response");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            resp.put("type", "tool_calls");
            resp.set("tool_calls", toToolCallsArray(toolCalls));
            return;
        }
        resp.put("type", "text");
        resp.put("content", finalText != null ? finalText : "");
        if (thinkingText != null && !thinkingText.isBlank()) {
            resp.put("thinking", thinkingText);
        }
    }

    public void finishTurn(TurnTrace turn, String finalReply, String errorMessageOrNull) {
        try {
            Files.createDirectories(logDir);
            ObjectNode root = JSON.createObjectNode();
            root.put("turn_id", turn.turnId());
            root.put("mode", turn.mode());
            root.put("session_id", turn.sessionId());
            root.put("started_at", DateTimeFormatter.ISO_INSTANT.format(turn.startedAt()));
            root.put("finished_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            root.put("user_input", turn.userInput());
            root.put("final_reply", finalReply != null ? finalReply : "");
            if (errorMessageOrNull != null && !errorMessageOrNull.isBlank()) {
                root.put("error", errorMessageOrNull);
            }
            root.set("rounds", turn.rounds());
            write(root, turn.sessionId(), turn.turnId());
        } catch (Exception e) {
            log.warn("写入 turn 日志失败(session={}): {}", turn.sessionId(), e.getMessage());
        }
    }

    private static ArrayNode toToolNames(List<ToolDefinition> tools) {
        ArrayNode toolNames = JSON.createArrayNode();
        for (ToolDefinition d : tools) {
            toolNames.add(d.getName());
        }
        return toolNames;
    }

    private void write(ObjectNode root, String sessionId, String turnId) throws Exception {
        String safeSession = sessionId == null ? "no-session" : sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(java.time.LocalDateTime.now());
        Path file = logDir.resolve(ts + "-" + safeSession + "-turn-" + turnId + ".json");
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
    }

    private static ArrayNode toMessagesArray(List<LlmMessage> requestMessages) {
        ArrayNode arr = JSON.createArrayNode();
        for (LlmMessage m : requestMessages) {
            ObjectNode n = arr.addObject();
            n.put("role", m.role());
            n.put("content", m.content());
            if (m.toolCallId() != null) {
                n.put("tool_call_id", m.toolCallId());
            }
            if (m.hasToolCalls()) {
                n.set("tool_calls", toToolCallsArray(m.toolCalls()));
            }
        }
        return arr;
    }

    private static ArrayNode toToolCallsArray(List<ToolCall> calls) {
        ArrayNode arr = JSON.createArrayNode();
        for (ToolCall c : calls) {
            ObjectNode n = arr.addObject();
            n.put("id", c.id());
            n.put("name", c.name());
            n.put("arguments", c.argumentsJson());
        }
        return arr;
    }

    private static void putRequestPayload(ObjectNode target, String requestPayload) {
        String raw = requestPayload != null ? requestPayload : "";
        if (raw.isBlank()) {
            target.put("request_payload_raw", "");
            return;
        }
        try {
            target.set("request_payload", JSON.readTree(raw));
        } catch (Exception e) {
            // 兼容非 JSON provider 或异常信息文本，仍保留原始内容供排查。
            target.put("request_payload_raw", raw);
        }
    }
}
