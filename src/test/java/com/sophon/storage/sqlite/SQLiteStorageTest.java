package com.sophon.storage.sqlite;

import com.sophon.model.Message;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteStorageTest {

    @Test
    void crud_session_and_message(@TempDir Path tempDir) {
        Path db = tempDir.resolve("t.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            Instant t0 = Instant.now();
            Session s = new Session(SQLiteStorage.newId(), "t", t0, t0);
            storage.upsertSession(s);
            assertTrue(storage.findSessionById(s.getId()).isPresent());

            int seq = storage.nextMessageSequence(s.getId());
            assertEquals(1, seq);
            Message m =
                    new Message(SQLiteStorage.newId(), s.getId(), MessageRole.USER, "hi", seq, Instant.now());
            storage.upsertMessage(m);

            List<Message> list = storage.listMessagesBySessionId(s.getId());
            assertEquals(1, list.size());
            assertEquals("hi", list.get(0).getContent());

            storage.deleteMessage(m.getId());
            assertTrue(storage.findMessageById(m.getId()).isEmpty());

            storage.deleteSession(s.getId());
            assertTrue(storage.findSessionById(s.getId()).isEmpty());
        }
    }

    @Test
    void deleteSession_cascadesMessages(@TempDir Path tempDir) {
        Path db = tempDir.resolve("cascade.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            Instant now = Instant.now();
            String sid = SQLiteStorage.newId();
            storage.upsertSession(new Session(sid, "x", now, now));
            Message m =
                    new Message(SQLiteStorage.newId(), sid, MessageRole.USER, "a", 1, now);
            storage.upsertMessage(m);
            storage.deleteSession(sid);
            assertTrue(storage.listMessagesBySessionId(sid).isEmpty());
            assertTrue(storage.findMessageById(m.getId()).isEmpty());
        }
    }

    @Test
    void saveMessagesInTransaction_commitsTogether(@TempDir Path tempDir) {
        Path db = tempDir.resolve("tx.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            Instant now = Instant.now();
            String sid = SQLiteStorage.newId();
            storage.upsertSession(new Session(sid, "tx", now, now));
            Message a = new Message(SQLiteStorage.newId(), sid, MessageRole.USER, "1", 1, now);
            Message b = new Message(SQLiteStorage.newId(), sid, MessageRole.ASSISTANT, "2", 2, now);
            storage.saveMessagesInTransaction(List.of(a, b));
            assertEquals(2, storage.listMessagesBySessionId(sid).size());
        }
    }

    @Test
    void persistence_survivesNewConnection(@TempDir Path tempDir) {
        Path db = tempDir.resolve("persist.db");
        String sid;
        try (SQLiteStorage s1 = new SQLiteStorage(db)) {
            s1.initialize();
            sid = SQLiteStorage.newId();
            s1.upsertSession(new Session(sid, "p", Instant.now(), Instant.now()));
            s1.upsertMessage(
                    new Message(
                            SQLiteStorage.newId(),
                            sid,
                            MessageRole.USER,
                            "stored",
                            1,
                            Instant.now()));
        }
        try (SQLiteStorage s2 = new SQLiteStorage(db)) {
            s2.initialize();
            assertTrue(s2.findSessionById(sid).isPresent());
            assertEquals("p", s2.findSessionById(sid).orElseThrow().getTitle());
            assertEquals(1, s2.listMessagesBySessionId(sid).size());
            assertEquals("stored", s2.listMessagesBySessionId(sid).get(0).getContent());
        }
    }

    @Test
    void findById_returnsEmptyWhenMissing(@TempDir Path tempDir) {
        Path db = tempDir.resolve("miss.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            assertTrue(storage.findSessionById("no-such-id").isEmpty());
            assertTrue(storage.findMessageById("no-such-id").isEmpty());
        }
    }

    @Test
    void splitStatements_handlesSchemaFile() throws Exception {
        try (var in = SQLiteStorage.class.getClassLoader().getResourceAsStream("db/schema.sql")) {
            assertTrue(in != null);
            String sql = SQLiteStorage.readAll(in);
            List<String> parts = SQLiteStorage.splitStatements(sql);
            assertFalse(parts.isEmpty());
            assertTrue(parts.stream().anyMatch(p -> p.contains("sessions")));
        }
    }
}
