package com.sophon.core.session;

import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import com.sophon.storage.sqlite.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionManagerTest {

    @Test
    void appendMessage_updatesSessionTimestamp(@TempDir Path tempDir) {
        Path db = tempDir.resolve("sm.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            Session s = sm.createSession("t");
            sm.appendMessage(s.getId(), MessageRole.USER, "x");
            assertEquals(1, sm.listMessages(s.getId()).size());
        }
    }

    @Test
    void appendMessage_rejectsUnknownSession(@TempDir Path tempDir) {
        Path db = tempDir.resolve("bad.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            assertThrows(IllegalArgumentException.class, () -> sm.appendMessage("nope", MessageRole.USER, "a"));
        }
    }
}
