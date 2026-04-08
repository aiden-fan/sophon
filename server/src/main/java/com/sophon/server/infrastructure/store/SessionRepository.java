package com.sophon.server.infrastructure.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.common.dto.SessionCreateRequest;
import com.sophon.common.dto.SessionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepository {

    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SessionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public SessionResponse create(SessionCreateRequest req) {
        String id = "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String name = (req == null || req.name() == null || req.name().isBlank()) ? "session" : req.name().trim();
        String provider = null;
        String model = null;
        if (req != null && req.llm() != null) {
            provider = asString(req.llm().get("provider"));
            model = asString(req.llm().get("model"));
        }
        List<String> tools = req == null || req.tools() == null ? List.of() : req.tools();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO sessions(id,name,llm_provider,llm_model,tools_json,created_at) VALUES (?,?,?,?,?,?)",
                id, name, provider, model, writeJson(tools), now.toString()
        );
        return new SessionResponse(id, name, now, provider, model, tools);
    }

    public List<SessionResponse> list() {
        return jdbcTemplate.query(
                "SELECT id,name,llm_provider,llm_model,tools_json,created_at FROM sessions ORDER BY created_at DESC",
                (rs, rowNum) -> mapSession(rs)
        );
    }

    public Optional<SessionResponse> get(String sessionId) {
        List<SessionResponse> list = jdbcTemplate.query(
                "SELECT id,name,llm_provider,llm_model,tools_json,created_at FROM sessions WHERE id=?",
                (rs, rowNum) -> mapSession(rs),
                sessionId
        );
        return list.stream().findFirst();
    }

    public boolean delete(String sessionId) {
        return jdbcTemplate.update("DELETE FROM sessions WHERE id=?", sessionId) > 0;
    }

    private SessionResponse mapSession(ResultSet rs) throws SQLException {
        return new SessionResponse(
                rs.getString("id"),
                rs.getString("name"),
                Instant.parse(rs.getString("created_at")),
                rs.getString("llm_provider"),
                rs.getString("llm_model"),
                readTools(rs.getString("tools_json"))
        );
    }

    private List<String> readTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
