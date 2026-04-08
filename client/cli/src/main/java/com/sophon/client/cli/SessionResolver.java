package com.sophon.client.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophon.common.dto.SessionResponse;

import java.util.List;
import java.util.Map;

final class SessionResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private SessionResolver() {}

    static String resolveOrCreate(String baseUrl, String token, String sessionIdOrNull) throws Exception {
        String root = HealthCommand.resolveBaseUrl(baseUrl).replaceAll("/+$", "");
        String wanted = (sessionIdOrNull == null || sessionIdOrNull.isBlank()) ? "main" : sessionIdOrNull.trim();
        String listUrl = root + "/api/v1/sessions";
        String listJson = HttpSupport.getJson(listUrl, token);
        List<SessionResponse> sessions = MAPPER.readValue(listJson, new TypeReference<>() {});
        for (SessionResponse s : sessions) {
            if (wanted.equals(s.id()) || wanted.equals(s.name())) {
                return s.id();
            }
        }
        String createUrl = root + "/api/v1/sessions";
        String createBody = MAPPER.writeValueAsString(Map.of(
                "name", wanted,
                "llm", Map.of("provider", "dashscope", "model", "qwen-max"),
                "tools", java.util.List.of("echo", "time")
        ));
        String createdJson = HttpSupport.postJson(createUrl, createBody, token);
        SessionResponse created = MAPPER.readValue(createdJson, SessionResponse.class);
        if ("main".equals(wanted)) {
            System.out.println("已自动创建默认会话: main (" + created.id() + ")");
        }
        return created.id();
    }
}
