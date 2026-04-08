package com.sophon.server.infrastructure.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.common.dto.Citation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MessageRepository {

    private static final TypeReference<List<Citation>> CITATIONS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MessageRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String save(String sessionId, String role, String content, List<Citation> citations) {
        String id = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update(
                "INSERT INTO messages(id,session_id,role,content,citations_json,created_at) VALUES (?,?,?,?,?,?)",
                id, sessionId, role, content, writeJson(citations), Instant.now().toString()
        );
        return id;
    }

    public List<MessageRow> listRecent(String sessionId, int limit) {
        return jdbcTemplate.query(
                "SELECT id,role,content,citations_json,created_at FROM messages WHERE session_id=? ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new MessageRow(
                        rs.getString("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        readCitations(rs.getString("citations_json")),
                        Instant.parse(rs.getString("created_at"))
                ),
                sessionId, limit
        );
    }

    private List<Citation> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CITATIONS_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    public record MessageRow(
            String id,
            String role,
            String content,
            List<Citation> citations,
            Instant createdAt
    ) {}
}
