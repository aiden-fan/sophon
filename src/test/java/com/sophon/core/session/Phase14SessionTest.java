package com.sophon.core.session;

import com.sophon.model.Message;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.storage.sqlite.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase14SessionTest {

    @Test
    void exportImport_roundtrip_newSession(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("p14.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session a = sm.createSession("orig");
            sm.updateCapabilities(
                    a.getId(),
                    new SessionCapabilityConfig(
                            Set.of("echo"), "sys", Set.of(), Set.of("kb1"), false, "elevated", 5));
            sm.appendMessage(a.getId(), MessageRole.USER, "hi");
            sm.appendMessage(a.getId(), MessageRole.ASSISTANT, "yo");

            String json = sm.exportSessionToJson(a.getId());
            Session b = sm.importSessionFromJson(json);

            assertTrue(!b.getId().equals(a.getId()));
            assertEquals("orig", b.getTitle());
            SessionCapabilityConfig c = b.getCapabilities();
            assertTrue(c.getDisabledToolNames().contains("echo"));
            assertEquals("sys", c.getSystemPrompt());
            assertTrue(c.getAttachedKnowledgeBaseNames().contains("kb1"));
            assertEquals("elevated", c.getTrustLevel());
            assertEquals(5, c.getToolRateLimitPerMinute());
            assertTrue(!c.isCitationsEnabled());

            List<Message> msgs = sm.listMessages(b.getId());
            assertEquals(2, msgs.size());
            assertEquals(MessageRole.USER, msgs.get(0).getRole());
            assertEquals("hi", msgs.get(0).getContent());
            assertEquals(1, msgs.get(0).getSequence());
            assertEquals(MessageRole.ASSISTANT, msgs.get(1).getRole());
            assertEquals(2, msgs.get(1).getSequence());
        }
    }

    @Test
    void profile_saveAndApply(@TempDir Path tempDir) {
        Path db = tempDir.resolve("p14c.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session a = sm.createSession("a");
            sm.setCitationsEnabled(a.getId(), false);
            sm.saveCapabilityProfile("p1", "预设一", a.getId());
            Session b = sm.createSession("b");
            sm.applyCapabilityProfile(b.getId(), "p1");
            Session bReload = sm.getSession(b.getId()).orElseThrow();
            assertTrue(!bReload.getCapabilities().isCitationsEnabled());
        }
    }

    @Test
    void fork_copiesCapabilitiesAndMessages(@TempDir Path tempDir) {
        Path db = tempDir.resolve("p14b.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session a = sm.createSession("main");
            sm.setToolDisabled(a.getId(), "echo", true);
            sm.appendMessage(a.getId(), MessageRole.USER, "one");

            Session fork = sm.forkSession(a.getId(), "branch");

            assertTrue(fork.getCapabilities().getDisabledToolNames().contains("echo"));
            List<Message> fm = sm.listMessages(fork.getId());
            assertEquals(1, fm.size());
            assertEquals("one", fm.get(0).getContent());
            assertEquals(fork.getId(), fm.get(0).getSessionId());
        }
    }
}
