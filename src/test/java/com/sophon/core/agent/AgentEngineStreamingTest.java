package com.sophon.core.agent;

import com.sophon.ai.MockAIProvider;
import com.sophon.ai.dto.CompletionResult;
import com.sophon.ai.dto.ModelOutputKind;
import com.sophon.ai.dto.StreamingChunk;
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
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEngineStreamingTest {

    @Test
    void runTurnStreaming_persistsAfterComplete(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("s.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            MockAIProvider mock = new MockAIProvider(msgs -> "ab");
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(1);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg);
            var s = sessions.createSession("s");
            StepVerifier.create(engine.runTurnStreaming(s.getId(), "hi").filter(c -> c instanceof StreamingChunk.TextToken))
                    .expectNextMatches(c -> c instanceof StreamingChunk.TextToken tt && "a".equals(tt.text()) && tt.kind() == ModelOutputKind.FINAL)
                    .expectNextMatches(c -> c instanceof StreamingChunk.TextToken tt && "b".equals(tt.text()) && tt.kind() == ModelOutputKind.FINAL)
                    .verifyComplete();
            var list = sessions.listMessages(s.getId());
            assertEquals(2, list.size());
            assertEquals(MessageRole.USER, list.get(0).getRole());
            assertEquals(MessageRole.ASSISTANT, list.get(1).getRole());
            assertEquals("ab", list.get(1).getContent());
        }
    }

    @Test
    void runTurnStreaming_emitsUtf8Chunks(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("s2.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            MockAIProvider mock = new MockAIProvider(msgs -> "你好");
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(1);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg);
            var s = sessions.createSession("s2");
            StepVerifier.create(engine.runTurnStreaming(s.getId(), "x").filter(c -> c instanceof StreamingChunk.TextToken))
                    .expectNextMatches(c -> c instanceof StreamingChunk.TextToken tt && "你".equals(tt.text()) && tt.kind() == ModelOutputKind.FINAL)
                    .expectNextMatches(c -> c instanceof StreamingChunk.TextToken tt && "好".equals(tt.text()) && tt.kind() == ModelOutputKind.FINAL)
                    .verifyComplete();
        }
    }

    @Test
    void runTurnStreaming_toolRoundThenText(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("stool.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            AtomicInteger calls = new AtomicInteger();
            MockAIProvider mock =
                    new MockAIProvider(
                            (msgs, toolDefs) -> {
                                int n = calls.incrementAndGet();
                                if (n == 1) {
                                    assertTrue(toolDefs.stream().anyMatch(d -> "echo".equals(d.getName())));
                                    return new CompletionResult.ToolCalls(
                                            List.of(new ToolCall("c1", "echo", "{\"text\":\"ping\"}")));
                                }
                                return new CompletionResult.Text("ok");
                            });
            AgentConfig cfg = new AgentConfig();
            cfg.setMaxRounds(4);
            cfg.setToolsEnabled(true);
            AgentEngine engine = new AgentEngine(sessions, conv, mock, cfg, tools, new ToolExecutor(tools));
            var s = sessions.createSession("st");
            StepVerifier.create(engine.runTurnStreaming(s.getId(), "hi").filter(c -> c instanceof StreamingChunk.TextToken))
                    .expectNextMatches(c -> c instanceof StreamingChunk.TextToken tt && "o".equals(tt.text()) && tt.kind() == ModelOutputKind.FINAL)
                    .expectNextMatches(c -> c instanceof StreamingChunk.TextToken tt && "k".equals(tt.text()) && tt.kind() == ModelOutputKind.FINAL)
                    .verifyComplete();
            assertEquals(2, calls.get());
            var list = sessions.listMessages(s.getId());
            assertEquals(4, list.size());
            assertTrue(list.get(1).getContent().contains("sophon_tool_calls_v1"));
            assertEquals(MessageRole.TOOL, list.get(2).getRole());
            assertEquals("c1\necho: ping", list.get(2).getContent());
            assertEquals("ok", list.get(3).getContent());
        }
    }
}
