package com.sophon.core.session;

import com.sophon.ai.MockAIProvider;
import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.ToolCall;
import com.sophon.config.AgentConfig;
import com.sophon.config.AiConfig;
import com.sophon.core.agent.AgentEngine;
import com.sophon.model.Session;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.EchoLocalTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCapabilityPhase6Test {

    @Test
    void disabledTool_excludedFromModelToolList(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p6.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            ToolExecutor exec = new ToolExecutor(tools, sessions);
            AtomicInteger calls = new AtomicInteger();
            MockAIProvider mock =
                    new MockAIProvider(
                            (msgs, toolsDef) -> {
                                calls.incrementAndGet();
                                assertTrue(toolsDef.stream().noneMatch(d -> "echo".equals(d.getName())));
                                return new CompletionResult.Text("ok");
                            });
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(1);
            cfg.setToolsEnabled(true);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg, tools, exec);
            var s = sessions.createSession("t");
            sessions.setToolDisabled(s.getId(), "echo", true);
            assertEquals("ok", engine.runTurn(s.getId(), "hi"));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void twoSessions_toolIsolation(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p6b.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            ToolExecutor exec = new ToolExecutor(tools, sessions);
            AtomicInteger calls = new AtomicInteger();
            MockAIProvider mock =
                    new MockAIProvider(
                            (msgs, toolsDef) -> {
                                int n = calls.incrementAndGet();
                                if (n == 1) {
                                    assertTrue(toolsDef.stream().anyMatch(d -> "echo".equals(d.getName())));
                                    return new CompletionResult.ToolCalls(
                                            List.of(new ToolCall("c1", "echo", "{\"text\":\"x\"}")));
                                }
                                return new CompletionResult.Text("done");
                            });
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(4);
            cfg.setToolsEnabled(true);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg, tools, exec);
            var disabled = sessions.createSession("a");
            var enabled = sessions.createSession("b");
            sessions.setToolDisabled(disabled.getId(), "echo", true);
            assertEquals("done", engine.runTurn(enabled.getId(), "x"));
        }
    }

    @Test
    void systemPrompt_persistsAcrossReopen(@TempDir Path tempDir) {
        Path db = tempDir.resolve("p6c.db");
        String sid;
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("Z");
            ConversationManager conv = new ConversationManager(sessions, ai);
            var s = sessions.createSession("t");
            sid = s.getId();
            conv.setSessionSystemPrompt(sid, "LOCAL");
        }
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("Z");
            ConversationManager conv = new ConversationManager(sessions, ai);
            assertEquals("LOCAL", conv.getEffectiveSystemPrompt(sid).orElseThrow());
        }
    }

    @Test
    void sessionRow_roundTripsCapabilitiesJson(@TempDir Path tempDir) {
        Path db = tempDir.resolve("p6d.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("c");
            SessionCapabilityConfig cap =
                    SessionCapabilityConfig.defaultNew()
                            .withDisabledToolNames(Set.of("echo"))
                            .withSystemPrompt("x");
            sessions.updateCapabilities(s.getId(), cap);
            Session loaded = sessions.getSession(s.getId()).orElseThrow();
            assertTrue(loaded.getCapabilities().getDisabledToolNames().contains("echo"));
            assertEquals("x", loaded.getCapabilities().getSystemPrompt());
        }
    }
}
