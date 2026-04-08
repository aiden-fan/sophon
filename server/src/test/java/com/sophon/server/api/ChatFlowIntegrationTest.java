package com.sophon.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureWebTestClient
class ChatFlowIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestThenChatReturnsCitationsAndStreamDoneStats() throws Exception {
        webTestClient.post()
                .uri("/api/v1/knowledge/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "sourceId", "it-doc-1",
                        "title", "Integration Doc",
                        "uri", "local://it/doc1",
                        "content", "Capability Registry unifies Tool Skill MCP Plugin with tracing"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ok").isEqualTo(true);

        byte[] sessionBytes = webTestClient.post()
                .uri("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "it-session",
                        "llm", Map.of("provider", "dashscope-mock", "model", "qwen-max"),
                        "tools", List.of("echo")
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        JsonNode session = objectMapper.readTree(new String(sessionBytes, StandardCharsets.UTF_8));
        String sessionId = session.path("id").asText();

        webTestClient.post()
                .uri("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "sessionId", sessionId,
                        "message", "Capability Registry 统一管理什么",
                        "stream", false,
                        "ragTopK", 2,
                        "ragMinScore", 0.01
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.citations").isArray()
                .jsonPath("$.citations.length()").value(v -> assertTrue(((Integer) v) >= 1));

        String sse = webTestClient.post()
                .uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of(
                        "sessionId", sessionId,
                        "message", "请流式回复一句话",
                        "stream", true
                ))
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .take(Duration.ofSeconds(4))
                .collectList()
                .map(list -> String.join("", list))
                .block(Duration.ofSeconds(6));

        assertTrue(sse != null && !sse.isBlank());
        assertTrue(sse.contains("\"latency_ms\""));
        assertTrue(sse.contains("\"estimated_tokens\""));
    }
}
