package com.sophon.knowledge.sqlite;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 使用主库 {@code kb_chunks} 表存储块与 float32 向量；检索时对单库全表扫并与查询向量算余弦相似度（alpha 规模可接受）。
 */
public final class SqliteVectorStore {

    private final Connection connection;

    public SqliteVectorStore(Connection connection) {
        this.connection = connection;
    }

    public void upsertChunk(String kbName, String chunkId, String content, float[] embedding) {
        String sql =
                "INSERT INTO kb_chunks (kb_name, chunk_id, content, embedding) VALUES (?,?,?,?) "
                        + "ON CONFLICT(kb_name, chunk_id) DO UPDATE SET "
                        + "content = excluded.content, embedding = excluded.embedding";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, kbName);
            ps.setString(2, chunkId);
            ps.setString(3, content);
            ps.setBytes(4, floatsToBlob(embedding));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertChunk", e);
        }
    }

    public void clearKnowledgeBase(String kbName) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM kb_chunks WHERE kb_name = ?")) {
            ps.setString(1, kbName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("clearKnowledgeBase", e);
        }
    }

    /**
     * 返回按 score 降序的前 {@code topK} 条。
     */
    public List<ScoredRow> topSimilar(String kbName, float[] queryVector, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        List<ScoredRow> all = new ArrayList<>();
        String sql = "SELECT chunk_id, content, embedding FROM kb_chunks WHERE kb_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, kbName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("chunk_id");
                    String content = rs.getString("content");
                    float[] vec = blobToFloats(rs.getBytes("embedding"));
                    double score = cosine(queryVector, vec);
                    all.add(new ScoredRow(id, content, score));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("topSimilar", e);
        }
        all.sort(Comparator.comparingDouble(ScoredRow::score).reversed());
        if (all.size() <= topK) {
            return List.copyOf(all);
        }
        return List.copyOf(all.subList(0, topK));
    }

    public record ScoredRow(String chunkId, String content, double score) {}

    /** 阶段 17：自检用向量块总数。 */
    public int countAllChunks() {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM kb_chunks")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("countAllChunks", e);
        }
    }

    static byte[] floatsToBlob(float[] f) {
        ByteBuffer bb = ByteBuffer.allocate(f.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : f) {
            bb.putFloat(v);
        }
        return bb.array();
    }

    static float[] blobToFloats(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return new float[0];
        }
        ByteBuffer bb = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] f = new float[blob.length / 4];
        for (int i = 0; i < f.length; i++) {
            f[i] = bb.getFloat();
        }
        return f;
    }

    static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na < 1e-12 || nb < 1e-12) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
