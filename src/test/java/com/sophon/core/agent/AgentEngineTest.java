package com.sophon.core.agent;

import com.sophon.ai.AIException;
import com.sophon.ai.MockAIProvider;
import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.ToolCall;
import com.sophon.config.AgentConfig;
import com.sophon.config.AiConfig;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.model.MessageRole;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.EchoLocalTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEngineTest {

    @Test
    void runTurn_singleRound_appendsUserAndAssistant(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("agent.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            AtomicInteger calls = new AtomicInteger();
            MockAIProvider mock =
                    new MockAIProvider(
                            msgs -> {
                                calls.incrementAndGet();
                                return "ok";
                            });
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(1);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg);
            var s = sessions.createSession("a");
            assertEquals("ok", engine.runTurn(s.getId(), "hi"));
            assertEquals(1, calls.get());
            var list = sessions.listMessages(s.getId());
            assertEquals(2, list.size());
            assertEquals(MessageRole.USER, list.get(0).getRole());
            assertEquals(MessageRole.ASSISTANT, list.get(1).getRole());
        }
    }

    @Test
    void runTurn_executesLocalTool_thenFinishesWithText(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("tool.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            ToolExecutor exec = new ToolExecutor(tools);
            AtomicInteger calls = new AtomicInteger();
            MockAIProvider mock =
                    new MockAIProvider(
                            (msgs, toolsDef) -> {
                                int n = calls.incrementAndGet();
                                if (n == 1) {
                                    assertTrue(toolsDef.stream().anyMatch(d -> "echo".equals(d.getName())));
                                    return new CompletionResult.ToolCalls(
                                            List.of(new ToolCall("c1", "echo", "{\"text\":\"ping\"}")));
                                }
                                return new CompletionResult.Text("done");
                            });
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(4);
            cfg.setToolsEnabled(true);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg, tools, exec);
            var s = sessions.createSession("t");
            assertEquals("done", engine.runTurn(s.getId(), "hi"));
            assertEquals(2, calls.get());
            var list = sessions.listMessages(s.getId());
            assertEquals(4, list.size());
            assertEquals(MessageRole.USER, list.get(0).getRole());
            assertEquals(MessageRole.ASSISTANT, list.get(1).getRole());
            assertTrue(list.get(1).getContent().contains("sophon_tool_calls_v1"));
            assertEquals(MessageRole.TOOL, list.get(2).getRole());
            assertEquals("c1\necho: ping", list.get(2).getContent());
            assertEquals(MessageRole.ASSISTANT, list.get(3).getRole());
        }
    }

    @Test
    void runTurn_toolsDisabled_doesNotPassToolDefinitions(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("off.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            MockAIProvider mock =
                    new MockAIProvider(
                            (msgs, toolsDef) -> {
                                assertTrue(toolsDef.isEmpty());
                                return new CompletionResult.Text("ok");
                            });
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(1);
            cfg.setToolsEnabled(false);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg, tools, new ToolExecutor(tools));
            var s = sessions.createSession("x");
            assertEquals("ok", engine.runTurn(s.getId(), "q"));
        }
    }

    @Test
    void runTurn_exhaustsMaxRoundsWithoutText_throws(@TempDir Path tempDir) {
        Path db = tempDir.resolve("exhaust.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            MockAIProvider mock =
                    new MockAIProvider(
                            (msgs, toolsDef) ->
                                    new CompletionResult.ToolCalls(
                                            List.of(new ToolCall("c1", "echo", "{\"text\":\"x\"}"))));
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(2);
            cfg.setToolsEnabled(true);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg, tools, new ToolExecutor(tools));
            var s = sessions.createSession("e");
            assertThrows(AIException.class, () -> engine.runTurn(s.getId(), "go"));
        }
    }
}
