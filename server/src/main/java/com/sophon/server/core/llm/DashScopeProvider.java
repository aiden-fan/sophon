package com.sophon.server.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DashScopeProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(DashScopeProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final DashScopeLlmProperties properties;
    private final ObjectMapper objectMapper;
    private final MockDashScopeProvider mock;
    private final OkHttpClient client;

    public DashScopeProvider(DashScopeLlmProperties properties, ObjectMapper objectMapper, MockDashScopeProvider mock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mock = mock;
        this.client = new OkHttpClient.Builder()
                .callTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String providerName() {
        return "dashscope";
    }

    @Override
    public Mono<String> generate(String model, List<LLMMessage> messages) {
        if (!canCallRemote()) {
            return mock.generate(modelOrDefault(model), messages);
        }
        return Mono.fromCallable(() -> callChatCompletion(modelOrDefault(model), messages))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("DashScope call failed, fallback to mock: {}", ex.toString());
                    return mock.generate(modelOrDefault(model), messages);
                });
    }

    @Override
    public Flux<String> streamGenerate(String model, List<LLMMessage> messages) {
        if (!canCallRemote()) {
            return mock.streamGenerate(modelOrDefault(model), messages);
        }
        return Flux.<String>create(emitter ->
                Schedulers.boundedElastic().schedule(() -> {
                    try {
                        streamChatCompletion(modelOrDefault(model), messages, emitter);
                    } catch (Exception ex) {
                        log.warn("DashScope stream failed, fallback to mock: {}", ex.toString());
                        mock.streamGenerate(modelOrDefault(model), messages)
                                .doOnNext(emitter::next)
                                .doOnComplete(emitter::complete)
                                .subscribe();
                    }
                })
        );
    }

    private String callChatCompletion(String model, List<LLMMessage> messages) throws Exception {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList());
        String body = objectMapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("dashscope http " + response.code());
            }
            String json = response.body().string();
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("dashscope empty choices");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException("dashscope empty content");
            }
            return content;
        }
    }

    private void streamChatCompletion(String model, List<LLMMessage> messages, reactor.core.publisher.FluxSink<String> emitter) throws Exception {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("stream", true);
        payload.put("messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList());
        String body = objectMapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("dashscope stream http " + response.code());
            }
            var source = response.body().source();
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                try {
                    JsonNode root = objectMapper.readTree(data);
                    String delta = root.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        emitter.next(delta);
                    }
                } catch (Exception ignore) {
                    // Ignore malformed partial lines from providers/proxies and keep streaming.
                }
            }
            emitter.complete();
        }
    }

    private boolean canCallRemote() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private String modelOrDefault(String model) {
        return (model == null || model.isBlank()) ? properties.getModel() : model;
    }
}
