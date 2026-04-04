package com.sophon.core.application;

import com.sophon.ai.MockAIProvider;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.config.AgentConfig;
import com.sophon.config.AiConfig;
import com.sophon.core.agent.AgentEngine;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.model.MessageRole;
import com.sophon.storage.sqlite.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatApplicationServiceTest {

    @Test
    void multiTurn_persistsAndCallsMock(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("chat.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ConversationManager conv = new ConversationManager(sessions, ai);
            AtomicInteger round = new AtomicInteger();
            MockAIProvider mock =
                    new MockAIProvider(
                            msgs -> {
                                int r = round.incrementAndGet();
                                assertTrue(msgs.stream().anyMatch(m -> "user".equals(m.role())));
                                return "reply-" + r;
                            });
            AgentEngine agent = new AgentEngine(sessions, conv, mock, new AgentConfig());
            ChatApplicationService chat = new ChatApplicationService(agent);
            var s = sessions.createSession("t");
            assertEquals("reply-1", chat.chat(s.getId(), "hi"));
            assertEquals("reply-2", chat.chat(s.getId(), "again"));
            var list = sessions.listMessages(s.getId());
            assertEquals(4, list.size());
            assertEquals(MessageRole.USER, list.get(0).getRole());
            assertEquals("hi", list.get(0).getContent());
            assertEquals(MessageRole.ASSISTANT, list.get(1).getRole());
            assertEquals("reply-1", list.get(1).getContent());
        }
    }

    @Test
    void buildContext_includesUserTurn(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("ctx.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("SYS");
            ConversationManager conv = new ConversationManager(sessions, ai);
            List<LlmMessage> captured = new ArrayList<>();
            MockAIProvider mock =
                    new MockAIProvider(
                            msgs -> {
                                captured.clear();
                                captured.addAll(msgs);
                                return "ok";
                            });
            AgentEngine agent = new AgentEngine(sessions, conv, mock, new AgentConfig());
            ChatApplicationService chat = new ChatApplicationService(agent);
            var s = sessions.createSession("x");
            chat.chat(s.getId(), "hello");
            assertTrue(captured.stream().anyMatch(m -> "system".equals(m.role()) && "SYS".equals(m.content())));
            assertTrue(captured.stream().anyMatch(m -> "user".equals(m.role()) && "hello".equals(m.content())));
        }
    }
}
