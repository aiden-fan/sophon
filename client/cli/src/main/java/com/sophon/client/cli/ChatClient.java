package com.sophon.client.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.common.dto.ChatResponse;

import java.util.LinkedHashMap;
import java.util.Map;

final class ChatClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatClient() {}

    static void run(
            String baseUrl,
            String token,
            String sessionIdOrName,
            String message,
            boolean stream,
            Integer ragTopK,
            Double ragMinScore
    ) throws Exception {
        String root = HealthCommand.resolveBaseUrl(baseUrl);
        String resolvedSessionId = SessionResolver.resolveOrCreate(root, token, sessionIdOrName);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", resolvedSessionId);
        payload.put("message", message);
        payload.put("stream", stream);
        if (ragTopK != null) {
            payload.put("ragTopK", ragTopK);
        }
        if (ragMinScore != null) {
            payload.put("ragMinScore", ragMinScore);
        }

        if (stream) {
            String url = root.replaceAll("/+$", "") + "/api/v1/chat/stream";
            String body = MAPPER.writeValueAsString(payload);
            final StringBuilder done = new StringBuilder();
            HttpSupport.postSse(url, body, token, line -> {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.startsWith("{") && data.contains("\"message_id\"")) {
                        done.setLength(0);
                        done.append(data);
                    } else {
                        System.out.print(data);
                    }
                }
            });
            System.out.println();
            if (done.length() > 0) {
                System.out.println("-- done -- " + done);
            }
            return;
        }

        String url = root.replaceAll("/+$", "") + "/api/v1/chat";
        String body = MAPPER.writeValueAsString(payload);
        String json = HttpSupport.postJson(url, body, token);
        ChatResponse response = MAPPER.readValue(json, ChatResponse.class);
        System.out.println(response.content());
        if (response.citations() != null && !response.citations().isEmpty()) {
            System.out.println("-- citations --");
            response.citations().forEach(c -> System.out.println(c.sourceId() + " " + c.uri()));
        }
    }
}
