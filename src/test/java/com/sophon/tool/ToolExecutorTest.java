package com.sophon.tool;

import com.sophon.ai.dto.ToolCall;
import com.sophon.core.session.SessionManager;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.local.EchoLocalTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

    @Test
    void executesRegisteredTool() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new EchoLocalTool());
        ToolExecutor exec = new ToolExecutor(reg);
        ToolResult r = exec.execute(new ToolCall("c1", "echo", "{\"text\":\"hello\"}"));
        assertEquals(true, r.success());
        assertEquals("echo: hello", r.content());
    }

    @Test
    void unknownTool_throws() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new EchoLocalTool());
        ToolExecutor exec = new ToolExecutor(reg);
        assertThrows(IllegalStateException.class, () -> exec.execute(new ToolCall("c1", "missing", "{}")));
    }

    @Test
    void sessionDisabledTool_returnsErrorWithoutExecuting(@TempDir Path tempDir) {
        Path db = tempDir.resolve("tex.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("x");
            sm.setToolDisabled(s.getId(), "echo", true);
            ToolRegistry reg = new ToolRegistry();
            reg.register(new EchoLocalTool());
            ToolExecutor exec = new ToolExecutor(reg, sm);
            ToolResult r = exec.execute(s.getId(), new ToolCall("c1", "echo", "{\"text\":\"nope\"}"));
            assertFalse(r.success());
            assertTrue(r.content().contains("禁用"));
        }
    }
}
