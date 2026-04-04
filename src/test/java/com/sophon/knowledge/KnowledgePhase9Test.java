package com.sophon.knowledge;

import com.sophon.knowledge.embedding.HashEmbeddingProvider;
import com.sophon.knowledge.index.DocumentIndexer;
import com.sophon.knowledge.model.VectorSearchHit;
import com.sophon.knowledge.sqlite.SqliteVectorStore;
import com.sophon.storage.sqlite.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgePhase9Test {

    @Test
    void documentIndexer_splitsChunks() {
        String t = "a".repeat(100);
        List<String> c = DocumentIndexer.chunkText(t, 32);
        assertEquals(4, c.size());
        assertEquals(32, c.get(0).length());
    }

    @Test
    void ingestAndSearch_returnsHits(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("k9.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SqliteVectorStore vec = new SqliteVectorStore(storage.jdbcConnection());
            KnowledgeManager km = new KnowledgeManager(new HashEmbeddingProvider(64), vec);
            km.ingestText("demo", "readme", "Sophon 项目支持 RAG 与本地知识库检索。");
            List<VectorSearchHit> hits = km.search("demo", "RAG 知识库", 5);
            assertFalse(hits.isEmpty());
            assertTrue(hits.get(0).content().contains("RAG") || hits.get(0).score() >= 0);
        }
    }
}
