package com.sophon.core.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.unified.*;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云 Dashscope（通义千问）Provider
 * 通过 HTTP 调用，不依赖特定 SDK
 */
public class DashscopeProvider implements LLMProvider {

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_RETRIES = 2;

    private final String apiKey;
    private final String defaultModel;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public DashscopeProvider(String apiKey) {
        this(apiKey, "qwen-plus");
    }

    public DashscopeProvider(String apiKey, String defaultModel) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public UnifiedChatResponse complete(UnifiedChatRequest request) {
        ensureApiKey();
        try {
            ObjectNode body = buildRequestBody(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

            return executeWithRetry(httpRequest);
        } catch (Exception e) {
            throw new RuntimeException("Dashscope API 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Publisher<UnifiedStreamEvent> stream(UnifiedChatRequest request) {
        // Phase 1: 用同步 complete 模拟流式输出
        return subscriber -> {
            try {
                UnifiedChatResponse response = complete(request);
                subscriber.onSubscribe(new Subscription() {
                    private boolean done = false;
                    @Override public void request(long n) {
                        if (!done) {
                            String content = response.content();
                            for (int i = 0; i < content.length(); i++) {
                                subscriber.onNext(new UnifiedStreamEvent(String.valueOf(content.charAt(i)), false));
                            }
                            subscriber.onNext(UnifiedStreamEvent.finish());
                            subscriber.onComplete();
                            done = true;
                        }
                    }
                    @Override public void cancel() { done = true; }
                });
            } catch (Exception e) {
                subscriber.onError(e);
            }
        };
    }

    @Override
    public String providerName() {
        return "dashscope";
    }

    private UnifiedChatResponse executeWithRetry(HttpRequest httpRequest) throws Exception {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return parseResponse(response.body());
                }

                String body = response.body() == null ? "" : response.body();
                boolean canRetry = status == 429 || status >= 500;
                if (!canRetry || attempt == MAX_RETRIES) {
                    throw new RuntimeException("Dashscope 返回错误: HTTP " + status + " - " + body);
                }
                sleepBackoff(attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("请求被中断", ie);
            } catch (Exception e) {
                lastError = new RuntimeException("请求失败: " + e.getMessage(), e);
                if (attempt == MAX_RETRIES) break;
                sleepBackoff(attempt);
            }
        }
        throw lastError != null ? lastError : new RuntimeException("Dashscope 请求失败");
    }

    private void sleepBackoff(int attempt) throws InterruptedException {
        long sleepMs = 500L * (1L << attempt);
        Thread.sleep(sleepMs);
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY");
        }
    }

    private ObjectNode buildRequestBody(UnifiedChatRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model().equals("mock") ? defaultModel : request.model());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());

        ArrayNode messagesNode = mapper.createArrayNode();
        for (UnifiedMessage msg : request.messages()) {
            ObjectNode msgNode = mapper.createObjectNode();
            msgNode.put("role", msg.role().name().toLowerCase());
            msgNode.put("content", msg.content());
            if (msg.role() == UnifiedRole.ASSISTANT && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode toolCallsNode = mapper.createArrayNode();
                for (ToolCall call : msg.toolCalls()) {
                    ObjectNode toolCallNode = mapper.createObjectNode();
                    toolCallNode.put("id", call.id() == null ? "" : call.id());
                    toolCallNode.put("type", "function");
                    ObjectNode functionNode = mapper.createObjectNode();
                    functionNode.put("name", call.name());
                    functionNode.put("arguments", call.argsJson());
                    toolCallNode.set("function", functionNode);
                    toolCallsNode.add(toolCallNode);
                }
                msgNode.set("tool_calls", toolCallsNode);
            }
            if (msg.toolCallId() != null && !msg.toolCallId().isBlank()) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }
            if (msg.toolName() != null && !msg.toolName().isBlank()) {
                msgNode.put("name", msg.toolName());
            }
            messagesNode.add(msgNode);
        }
        body.set("messages", messagesNode);

        if (!request.tools().isEmpty()) {
            ArrayNode toolsNode = mapper.createArrayNode();
            for (UnifiedTool tool : request.tools()) {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode functionNode = mapper.createObjectNode();
                functionNode.put("name", tool.name());
                functionNode.put("description", tool.description());
                functionNode.set("parameters", parseJsonSafely(tool.parametersJsonSchema()));
                toolNode.set("function", functionNode);
                toolsNode.add(toolNode);
            }
            body.set("tools", toolsNode);
        }

        return body;
    }

    private UnifiedChatResponse parseResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("响应缺少 choices");
            }
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
            String finishReason = choice.path("finish_reason").asText("stop");

            // Parse tool calls
            List<ToolCall> toolCalls = List.of();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (!toolCallsNode.isMissingNode() && toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode tc : toolCallsNode) {
                    String id = tc.path("id").asText(null);
                    String name = tc.path("function").path("name").asText();
                    String args = tc.path("function").path("arguments").asText();
                    calls.add(new ToolCall(id, name, args));
                }
                toolCalls = calls;
            }

            JsonNode usage = root.path("usage");
            UnifiedChatResponse.Usage u = null;
            if (!usage.isMissingNode()) {
                u = new UnifiedChatResponse.Usage(
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0)
                );
            }

            return new UnifiedChatResponse(content, toolCalls, u, finishReason);
        } catch (Exception e) {
            throw new RuntimeException("解析 Dashscope 响应失败: " + json, e);
        }
    }

    private JsonNode parseJsonSafely(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
