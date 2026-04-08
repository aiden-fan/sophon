package com.sophon.server.infrastructure.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class KnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String insertChunk(String sourceId, String title, String uri, String content, String embeddingJson) {
        String id = "chunk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update(
                "INSERT INTO kb_chunks(id,source_id,title,uri,content,embedding_json,created_at) VALUES (?,?,?,?,?,?,?)",
                id, sourceId, title, uri, content, embeddingJson, Instant.now().toString()
        );
        return id;
    }

    public List<ChunkRow> listAll() {
        return jdbcTemplate.query(
                "SELECT id,source_id,title,uri,content,embedding_json,created_at FROM kb_chunks",
                (rs, rowNum) -> new ChunkRow(
                        rs.getString("id"),
                        rs.getString("source_id"),
                        rs.getString("title"),
                        rs.getString("uri"),
                        rs.getString("content"),
                        rs.getString("embedding_json"),
                        Instant.parse(rs.getString("created_at"))
                )
        );
    }

    public record ChunkRow(
            String id,
            String sourceId,
            String title,
            String uri,
            String content,
            String embeddingJson,
            Instant createdAt
    ) {}
}
