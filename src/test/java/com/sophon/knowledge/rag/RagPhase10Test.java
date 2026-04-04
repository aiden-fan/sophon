package com.sophon.knowledge.rag;

import com.sophon.ai.dto.LlmMessage;
import com.sophon.core.session.SessionManager;
import com.sophon.knowledge.KnowledgeManager;
import com.sophon.knowledge.embedding.HashEmbeddingProvider;
import com.sophon.knowledge.sqlite.SqliteVectorStore;
import com.sophon.storage.sqlite.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPhase10Test {

    @Test
    void augment_respectsAttachedKbOnly(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("r10.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            SqliteVectorStore vec = new SqliteVectorStore(storage.jdbcConnection());
            KnowledgeManager km = new KnowledgeManager(new HashEmbeddingProvider(64), vec);
            km.ingestText("kb1", "d1", "Alpha 独有内容 xyz123");
            km.ingestText("kb2", "d2", "Beta 独有内容 abc999");
            sessions.attachKnowledgeBase(s.getId(), "kb1");
            RAGEngine rag = new RAGEngine(km, sessions);
            List<LlmMessage> base =
                    List.of(LlmMessage.system("sys"), LlmMessage.user("查询 Alpha 独有"));
            List<LlmMessage> aug = rag.augmentForChat(s.getId(), base);
            assertEquals(3, aug.size());
            String ragSys = aug.get(1).content();
            assertTrue(ragSys.contains("Alpha") || ragSys.contains("xyz123"));
            assertFalse(ragSys.contains("abc999"));
        }
    }

    @Test
    void citationsFlag_changesRagBlock(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("r10b.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            SqliteVectorStore vec = new SqliteVectorStore(storage.jdbcConnection());
            KnowledgeManager km = new KnowledgeManager(new HashEmbeddingProvider(64), vec);
            km.ingestText("k", "d", "Citation test chunk id visible");
            sessions.attachKnowledgeBase(s.getId(), "k");
            sessions.updateCapabilities(
                    s.getId(),
                    sessions
                            .getSession(s.getId())
                            .orElseThrow()
                            .getCapabilities()
                            .withCitationsEnabled(false));
            RAGEngine rag = new RAGEngine(km, sessions);
            List<LlmMessage> a =
                    rag.augmentForChat(s.getId(), List.of(LlmMessage.user("Citation test")));
            String off = extractRagBlock(a);
            sessions.updateCapabilities(
                    s.getId(),
                    sessions
                            .getSession(s.getId())
                            .orElseThrow()
                            .getCapabilities()
                            .withCitationsEnabled(true));
            List<LlmMessage> b =
                    rag.augmentForChat(s.getId(), List.of(LlmMessage.user("Citation test")));
            String on = extractRagBlock(b);
            assertTrue(on.contains("chunk=") || on.contains("【引用说明】"));
            assertTrue(off.length() <= on.length());
        }
    }

    private static String extractRagBlock(List<LlmMessage> m) {
        for (LlmMessage x : m) {
            if ("system".equals(x.role()) && x.content().startsWith("【知识库检索】")) {
                return x.content();
            }
        }
        return "";
    }
}
