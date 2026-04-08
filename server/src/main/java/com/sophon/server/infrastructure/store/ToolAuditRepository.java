package com.sophon.server.infrastructure.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class ToolAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public ToolAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(String toolName, boolean allowed, String reason, long latencyMs) {
        jdbcTemplate.update(
                "INSERT INTO tool_audit_logs(id,tool_name,allowed,reason,latency_ms,created_at) VALUES (?,?,?,?,?,?)",
                "audit_" + UUID.randomUUID().toString().substring(0, 12),
                toolName,
                allowed ? 1 : 0,
                reason,
                latencyMs,
                Instant.now().toString()
        );
    }
}
