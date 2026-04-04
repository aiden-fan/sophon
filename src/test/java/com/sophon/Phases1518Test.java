package com.sophon;

import com.sophon.bootstrap.SophonBootstrap;
import com.sophon.cli.DoctorService;
import com.sophon.config.AiConfig;
import com.sophon.config.AppConfig;
import com.sophon.config.ContextConfig;
import com.sophon.core.application.BatchRegressionRunner;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.i18n.CliMessages;
import com.sophon.model.MessageRole;
import com.sophon.model.MessageSearchHit;
import com.sophon.model.Session;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.EchoLocalTool;
import com.sophon.web.SophonWebRouter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phases1518Test {

    @Test
    void contextWindow_addsTruncationNotice(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("ctx.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session s = sm.createSession("c");
            sm.appendMessage(s.getId(), MessageRole.USER, "aaaaaaaaaaaaaaaaaaaa");
            sm.appendMessage(s.getId(), MessageRole.USER, "bbbbbbbbbbbbbbbbbbbb");
            sm.appendMessage(s.getId(), MessageRole.USER, "cccccccccccccccccccc");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ContextConfig ctx = new ContextConfig();
            ctx.setMaxChars(30);
            ctx.setTruncationNotice(true);
            ConversationManager cm = new ConversationManager(sm, ai, ctx);
            var msgs = cm.buildMessagesForModel(s.getId());
            assertTrue(
                    msgs.stream().anyMatch(m -> "system".equals(m.role()) && m.content().contains("省略")),
                    () -> msgs.toString());
        }
    }

    @Test
    void truncateFromLastUser_removesTail(@TempDir Path tempDir) {
        Path db = tempDir.resolve("reg.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session s = sm.createSession("r");
            sm.appendMessage(s.getId(), MessageRole.USER, "u1");
            sm.appendMessage(s.getId(), MessageRole.ASSISTANT, "a1");
            var u = sm.truncateFromLastUser(s.getId());
            assertTrue(u.isPresent());
            assertEquals("u1", u.get());
            assertTrue(sm.listMessages(s.getId()).isEmpty());
        }
    }

    @Test
    void ftsSearch_findsMessage(@TempDir Path tempDir) {
        Path db = tempDir.resolve("fts.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session s = sm.createSession("f");
            sm.appendMessage(s.getId(), MessageRole.USER, "uniquewolfxyz token");
            List<MessageSearchHit> hits = sm.searchMessages("uniquewolfxyz", 10);
            assertFalse(hits.isEmpty());
        }
    }

    @Test
    void doctorReport_mentionsTool() {
        AppConfig cfg = AppConfig.defaults();
        ToolRegistry tr = new ToolRegistry();
        tr.register(new EchoLocalTool());
        DoctorService doc = new DoctorService(cfg, tr, null, null, null);
        assertTrue(doc.runReport().contains("Tool"));
    }

    @Test
    void cliMessages_zh() {
        // 仅验证资源存在；不强制改 JVM 默认 Locale
        assertFalse(CliMessages.get("cli.banner.hint").isBlank());
    }

    @Test
    void batchRegressionRunner_ok(@TempDir Path tempDir) throws Exception {
        AppConfig cfg = AppConfig.defaults();
        cfg.getSophon().getDatabase().setPath(tempDir.resolve("ignore.db").toString());
        assertEquals(0, BatchRegressionRunner.run(cfg));
    }

    @Test
    void webflux_createSession(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("web.db");
        AppConfig cfg = AppConfig.defaults();
        cfg.getSophon().getDatabase().setPath(db.toString());
        try (SophonBootstrap.Handle h = SophonBootstrap.launch(cfg, db)) {
            var router = SophonWebRouter.build(h);
            WebTestClient client = WebTestClient.bindToRouterFunction(router).build();
            client.post().uri("/api/sessions").exchange().expectStatus().isOk().expectBody().jsonPath("$.id").exists();
        }
    }
}
