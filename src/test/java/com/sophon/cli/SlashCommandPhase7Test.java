package com.sophon.cli;

import com.sophon.config.AiConfig;
import com.sophon.core.application.SessionCapabilityService;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.model.MessageRole;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.EchoLocalTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandPhase7Test {

    @Test
    void nonSlashLine_notHandled(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            assertEquals(Optional.empty(), router.route("hello", "x"));
        }
    }

    @Test
    void help_doesNotAppendMessage(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7b.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            assertEquals(0, sessions.listMessages(s.getId()).size());
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            Optional<String> out = router.route("/help", s.getId());
            assertTrue(out.isPresent());
            assertEquals(0, sessions.listMessages(s.getId()).size());
        }
    }

    @Test
    void toolsDisable_persists(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7c.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            router.route("/tools disable echo", s.getId());
            assertTrue(
                    sessions
                            .getSession(s.getId())
                            .orElseThrow()
                            .getCapabilities()
                            .getDisabledToolNames()
                            .contains("echo"));
        }
    }

    @Test
    void unknownSlash_returnsMessage(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7d.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            Optional<String> out = router.route("/not_a_real_command_xxx", s.getId());
            assertTrue(out.isPresent());
            assertTrue(out.get().contains("未知命令"));
            assertEquals(0, sessions.listMessages(s.getId()).size());
        }
    }

    @Test
    void plainSlashStart_notChat(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7e.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            assertFalse(router.route("/foo", s.getId()).isEmpty());
        }
    }

    @Test
    void init_createsNovelWorkspaceTemplate(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7f.db");
        Path ws = tempDir.resolve("novel-work");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            Optional<String> out = router.route("/init " + ws, s.getId());
            assertTrue(out.isPresent());
            assertTrue(out.get().contains("初始化完成"));
            assertTrue(Files.exists(ws.resolve("prompts/base.md")));
            assertTrue(Files.exists(ws.resolve("prompts/tasks/chapter.md")));
            assertTrue(Files.exists(ws.resolve("story/characters/主角.md")));
            assertTrue(Files.exists(ws.resolve("story/chapter_outlines/第1章.md")));
            assertTrue(Files.exists(ws.resolve("chapters/第1章.md")));
        }
    }

    @Test
    void clearContext_removesAllMessages(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p7g.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            sessions.appendMessage(s.getId(), MessageRole.USER, "u1");
            sessions.appendMessage(s.getId(), MessageRole.ASSISTANT, "a1");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            SessionCapabilityService cap = new SessionCapabilityService(sessions);
            SlashCommandRouter router =
                    new SlashCommandRouter(cap, conv, sessions, tools, ai, List::of, null);
            Optional<String> out = router.route("/context clear", s.getId());
            assertTrue(out.isPresent());
            assertTrue(out.get().contains("已清空"));
            assertEquals(0, sessions.listMessages(s.getId()).size());
        }
    }
}
