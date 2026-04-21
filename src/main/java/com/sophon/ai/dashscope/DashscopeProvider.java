package com.sophon.ai.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophon.ai.AIException;
import com.sophon.ai.AIProvider;
import com.sophon.ai.LlmRequestInspectable;
import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.ModelOutputKind;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.ai.dto.ToolCall;
import com.sophon.config.AiConfig;
import com.sophon.tool.ToolDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 阿里云 Dashscope（OpenAI 兼容 Chat Completions）实现：同步与非流式、流式（SSE）。
 */
public class DashscopeProvider implements AIProvider, LlmRequestInspectable {

    private static final Logger log = LoggerFactory.getLogger(DashscopeProvider.class);

    private final AiConfig ai;
    /** 非空时覆盖 {@link AiConfig#getModel()}（用于备用模型等）。 */
    private final String modelOverride;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;

    public DashscopeProvider(AiConfig ai) {
        this(ai, null);
    }

    /** @param modelOverride 非空且非空白时作为请求 model */
    public DashscopeProvider(AiConfig ai, String modelOverride) {
        this.ai = ai;
        this.modelOverride = modelOverride != null && !modelOverride.isBlank() ? modelOverride.trim() : null;
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.min(30, Math.max(5, ai.getTimeoutSeconds()))))
                        .build();
    }

    private String effectiveModel() {
        return modelOverride != null ? modelOverride : ai.getModel();
    }

    @Override
    public String buildRequestPayload(List<LlmMessage> messages, boolean stream, List<ToolDefinition> tools)
            throws AIException {
        return writeBody(messages, stream, tools);
    }

    @Override
    public String providerLabel() {
        return "dashscope:" + effectiveModel();
    }

    @Override
    public CompletionResult complete(List<LlmMessage> messages, List<ToolDefinition> tools) throws AIException {
        validate(messages);
        String payload = writeBody(messages, false, tools);
        HttpRequest request = buildHttpRequest(payload);
        log.debug(
                "Dashscope 同步请求 model={} messages={} tools={}",
                effectiveModel(),
                messages.size(),
                tools == null ? 0 : tools.size());
        try {
            HttpResponse<String> resp =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseSyncResponse(resp.statusCode(), resp.body());
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException("调用 Dashscope 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamingChunk> completeStreaming(List<LlmMessage> messages, List<ToolDefinition> toolDefs) {
        return Flux.defer(
                () -> {
                    try {
                        validate(messages);
                        String payload = writeBody(messages, true, toolDefs);
                        HttpRequest request = buildHttpRequest(payload);
                        log.debug(
                                "Dashscope 流式请求 model={} messages={} tools={}",
                                effectiveModel(),
                                messages.size(),
                                toolDefs == null ? 0 : toolDefs.size());
                        return Flux.create(
                                sink ->
                                        Schedulers.boundedElastic()
                                                .schedule(
                                                        () -> {
                                                            try {
                                                                HttpResponse<InputStream> resp =
                                                                        http.send(
                                                                                request,
                                                                                HttpResponse.BodyHandlers.ofInputStream());
                                                                int status = resp.statusCode();
                                                                try (InputStream body = resp.body();
                                                                        BufferedReader reader =
                                                                                new BufferedReader(
                                                                                        new InputStreamReader(
                                                                                                body,
                                                                                                StandardCharsets.UTF_8))) {
                                                                    if (status < 200 || status >= 300) {
                                                                        String errBody = readAll(reader);
                                                                        sink.error(
                                                                                new AIException(
                                                                                        "Dashscope 流式错误 HTTP "
                                                                                                + status
                                                                                                + "："
                                                                                                + truncate(errBody)));
                                                                        return;
                                                                    }
                                                                    parseSseLines(reader, sink);
                                                                    sink.complete();
                                                                }
                                                            } catch (Throwable t) {
                                                                if (t instanceof AIException) {
                                                                    sink.error(t);
                                                                } else {
                                                                    sink.error(
                                                                            new AIException(
                                                                                    "流式读取失败: "
                                                                                            + t.getMessage(),
                                                                                    t));
                                                                }
                                                            }
                                                        }));
                    } catch (AIException e) {
                        return Flux.error(e);
                    } catch (Exception e) {
                        return Flux.error(new AIException(e.getMessage(), e));
                    }
                });
    }

    private void parseSseLines(BufferedReader reader, FluxSink<StreamingChunk> sink) throws AIException {
        Map<Integer, ToolCallStreamSlot> toolSlots = new HashMap<>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    flushStreamingToolCalls(toolSlots, sink);
                    break;
                }
                JsonNode node = json.readTree(data);
                JsonNode err = node.path("error");
                if (!err.isMissingNode() && !err.isNull()) {
                    String msg = err.path("message").asText(err.toString());
                    throw new AIException("Dashscope 流式业务错误：" + msg);
                }
                JsonNode delta = node.path("choices").path(0).path("delta");
                JsonNode reasoningNode = delta.path("reasoning_content");
                if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
                    String rp = reasoningNode.asText("");
                    if (!rp.isEmpty()) {
                        sink.next(new StreamingChunk.TextToken(rp, ModelOutputKind.THINKING));
                    }
                }
                JsonNode contentNode = delta.path("content");
                if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                    String piece = contentNode.asText("");
                    if (!piece.isEmpty()) {
                        sink.next(new StreamingChunk.TextToken(piece, ModelOutputKind.FINAL));
                    }
                }
                JsonNode deltaToolCalls = delta.path("tool_calls");
                if (deltaToolCalls.isArray()) {
                    for (JsonNode item : deltaToolCalls) {
                        mergeToolCallDelta(item, toolSlots);
                    }
                }
            }
            flushStreamingToolCalls(toolSlots, sink);
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException("解析流式响应失败: " + e.getMessage(), e);
        }
    }

    private static void mergeToolCallDelta(JsonNode item, Map<Integer, ToolCallStreamSlot> slots) {
        int index = item.path("index").asInt(-1);
        if (index < 0) {
            return;
        }
        ToolCallStreamSlot slot = slots.computeIfAbsent(index, k -> new ToolCallStreamSlot());
        JsonNode idNode = item.get("id");
        if (idNode != null && !idNode.isNull()) {
            String id = idNode.asText("");
            if (!id.isBlank()) {
                slot.id = id;
            }
        }
        JsonNode fn = item.path("function");
        if (fn.has("name")) {
            String name = fn.path("name").asText("");
            if (!name.isBlank()) {
                slot.name = name;
            }
        }
        if (fn.has("arguments")) {
            JsonNode a = fn.get("arguments");
            if (a.isTextual()) {
                slot.arguments.append(a.asText());
            } else if (!a.isNull() && !a.isMissingNode()) {
                slot.arguments.append(a.toString());
            }
        }
    }

    private void flushStreamingToolCalls(Map<Integer, ToolCallStreamSlot> slots, FluxSink<StreamingChunk> sink) {
        if (slots.isEmpty()) {
            return;
        }
        List<ToolCall> calls = new ArrayList<>();
        for (int idx : new TreeSet<>(slots.keySet())) {
            ToolCallStreamSlot s = slots.get(idx);
            if (s.id == null || s.id.isBlank() || s.name == null || s.name.isBlank()) {
                log.warn("流式 tool_calls 索引 {} 缺少 id 或 name，已跳过", idx);
                continue;
            }
            calls.add(new ToolCall(s.id, s.name, s.arguments.toString()));
        }
        slots.clear();
        if (!calls.isEmpty()) {
            sink.next(new StreamingChunk.ToolCallsFinished(calls));
        }
    }

    private static final class ToolCallStreamSlot {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    private static String readAll(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * 真实密钥优先从环境变量 {@code DASHSCOPE_API_KEY} 读取（推荐，勿将密钥写入仓库）；未设置时再使用 {@link com.sophon.config.AiConfig#getApiKey()}（来自 YAML / {@link com.sophon.config.ConfigManager} 合并结果）。
     */
    private String resolveApiKey() {
        String env = System.getenv("DASHSCOPE_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromConfig = ai.getApiKey();
        return fromConfig != null ? fromConfig.trim() : "";
    }

    private void validate(List<LlmMessage> messages) throws AIException {
        if (resolveApiKey().isBlank()) {
            throw new AIException(
                    "未配置 API 密钥：请设置环境变量 DASHSCOPE_API_KEY（推荐），或在 sophon.ai.api-key 中配置（勿提交到版本库）");
        }
        if (messages == null || messages.isEmpty()) {
            throw new AIException("消息列表不能为空");
        }
    }

    private String writeBody(List<LlmMessage> messages, boolean stream, List<ToolDefinition> toolDefs)
            throws AIException {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", effectiveModel());
            body.put("stream", stream);
            ArrayNode arr = body.putArray("messages");
            for (LlmMessage m : messages) {
                ObjectNode o = arr.addObject();
                o.put("role", m.role());
                if ("tool".equals(m.role())) {
                    o.put("content", m.content());
                    o.put("tool_call_id", m.toolCallId());
                } else if (m.hasToolCalls()) {
                    if (!m.content().isEmpty()) {
                        o.put("content", m.content());
                    } else {
                        o.putNull("content");
                    }
                    ArrayNode tcArr = o.putArray("tool_calls");
                    for (ToolCall call : m.toolCalls()) {
                        ObjectNode tc = tcArr.addObject();
                        tc.put("id", call.id());
                        tc.put("type", "function");
                        ObjectNode fn = tc.putObject("function");
                        fn.put("name", call.name());
                        fn.put("arguments", call.argumentsJson());
                    }
                } else {
                    o.put("content", m.content());
                }
            }
            if (toolDefs != null && !toolDefs.isEmpty()) {
                ArrayNode toolsArr = body.putArray("tools");
                for (ToolDefinition def : toolDefs) {
                    ObjectNode tool = toolsArr.addObject();
                    tool.put("type", "function");
                    ObjectNode fn = tool.putObject("function");
                    fn.put("name", def.getName());
                    fn.put("description", def.getDescription());
                    fn.set("parameters", def.getParametersSchema());
                }
            }
            return json.writeValueAsString(body);
        } catch (Exception e) {
            throw new AIException("序列化请求失败", e);
        }
    }

    private HttpRequest buildHttpRequest(String jsonPayload) {
        String base = ai.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/chat/completions";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(ai.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + resolveApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();
    }

    private CompletionResult parseSyncResponse(int status, String body) throws AIException {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new AIException("响应非 JSON，HTTP " + status + "：" + truncate(body), e);
        }
        if (status >= 200 && status < 300) {
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    String id = tc.path("id").asText("");
                    String type = tc.path("type").asText("");
                    JsonNode fn = tc.path("function");
                    String name = fn.path("name").asText("");
                    JsonNode argsNode = fn.path("arguments");
                    String args;
                    if (argsNode.isMissingNode() || argsNode.isNull()) {
                        args = "";
                    } else if (argsNode.isTextual()) {
                        args = argsNode.asText();
                    } else {
                        args = argsNode.toString();
                    }
                    if (id.isEmpty() || name.isEmpty()) {
                        throw new AIException("tool_calls 项缺少 id 或 name：" + truncate(body));
                    }
                    if (!"function".equals(type) && !type.isEmpty()) {
                        log.debug("tool_calls type 非 function: {}", type);
                    }
                    calls.add(new ToolCall(id, name, args));
                }
                return new CompletionResult.ToolCalls(calls);
            }
            String reasoning = message.path("reasoning_content").asText("");
            JsonNode contentNode = message.path("content");
            String text = contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");
            if (text.isEmpty() && reasoning.isEmpty()) {
                throw new AIException("响应中 assistant 既无 content 也无 reasoning_content：" + truncate(body));
            }
            return new CompletionResult.Text(text, reasoning.isBlank() ? null : reasoning);
        }
        String err = extractErrorMessage(root);
        throw new AIException("Dashscope 错误 HTTP " + status + "：" + err);
    }

    private static String extractErrorMessage(JsonNode root) {
        JsonNode e = root.path("error");
        if (e.isMissingNode() || e.isNull()) {
            return root.toString();
        }
        String msg = e.path("message").asText(null);
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return e.toString();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        int max = 800;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
