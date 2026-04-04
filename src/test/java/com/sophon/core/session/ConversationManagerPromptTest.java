package com.sophon.core.session;

import com.sophon.ai.dto.LlmMessage;
import com.sophon.config.AiConfig;
import com.sophon.storage.sqlite.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationManagerPromptTest {

    @Test
    void sessionOverrideOverridesGlobal(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("GLOBAL");
            ConversationManager conv = new ConversationManager(sessions, ai);
            var s = sessions.createSession("t");
            conv.setSessionSystemPrompt(s.getId(), "LOCAL");
            List<LlmMessage> ctx = conv.buildMessagesForModel(s.getId());
            assertEquals(1, ctx.size());
            assertEquals("system", ctx.get(0).role());
            assertEquals("LOCAL", ctx.get(0).content());
            conv.setSessionSystemPrompt(s.getId(), null);
            List<LlmMessage> ctx2 = conv.buildMessagesForModel(s.getId());
            assertTrue(ctx2.stream().anyMatch(m -> "system".equals(m.role()) && "GLOBAL".equals(m.content())));
        }
    }

    @Test
    void assistantStructuredJson_mapsContentOnlyToModel(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("seg.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            var s = sessions.createSession("seg");
            conv.appendAssistantSegments(s.getId(), "最终", "思考");
            List<LlmMessage> ctx = conv.buildMessagesForModel(s.getId());
            assertEquals(1, ctx.size());
            assertEquals("assistant", ctx.get(0).role());
            assertEquals("最终", ctx.get(0).content());
        }
    }
}
