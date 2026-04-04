package com.sophon.knowledge;

import com.sophon.knowledge.embedding.EmbeddingProvider;
import com.sophon.knowledge.index.DocumentIndexer;
import com.sophon.knowledge.model.VectorSearchHit;
import com.sophon.knowledge.sqlite.SqliteVectorStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库：文档切块、嵌入、写入 {@link SqliteVectorStore}，并按会话外由 {@link com.sophon.knowledge.rag.RAGEngine} 消费检索结果。
 */
public final class KnowledgeManager {

    private final EmbeddingProvider embedding;
    private final SqliteVectorStore store;
    private final int maxChunkChars;

    public KnowledgeManager(EmbeddingProvider embedding, SqliteVectorStore store, int maxChunkChars) {
        this.embedding = embedding;
        this.store = store;
        this.maxChunkChars = maxChunkChars;
    }

    public KnowledgeManager(EmbeddingProvider embedding, SqliteVectorStore store) {
        this(embedding, store, 600);
    }

    /** 将整段文本切块后写入 {@code kbName}；{@code documentKey} 用于生成稳定 chunk id 前缀。 */
    public void ingestText(String kbName, String documentKey, String text) {
        if (kbName == null || kbName.isBlank()) {
            throw new IllegalArgumentException("kbName 不能为空");
        }
        String key = documentKey == null || documentKey.isBlank() ? "doc" : documentKey.trim();
        List<String> chunks = DocumentIndexer.chunkText(text, maxChunkChars);
        int i = 0;
        for (String c : chunks) {
            String chunkId = key + "#" + (i++);
            store.upsertChunk(kbName, chunkId, c, embedding.embed(c));
        }
    }

    public List<VectorSearchHit> search(String kbName, String query, int topK) {
        float[] q = embedding.embed(query);
        List<SqliteVectorStore.ScoredRow> rows = store.topSimilar(kbName, q, topK);
        List<VectorSearchHit> out = new ArrayList<>();
        for (SqliteVectorStore.ScoredRow r : rows) {
            out.add(new VectorSearchHit(kbName, r.chunkId(), r.content(), r.score()));
        }
        return out;
    }

    public void clearKnowledgeBase(String kbName) {
        store.clearKnowledgeBase(kbName);
    }
}
