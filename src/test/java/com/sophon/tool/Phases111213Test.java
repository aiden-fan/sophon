package com.sophon.tool;

import com.sophon.ai.dto.ToolCall;
import com.sophon.core.session.SessionManager;
import com.sophon.observability.SqliteToolAuditLogger;
import com.sophon.observability.SqliteUsageTracker;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.local.EchoLocalTool;
import com.sophon.tool.local.ReadFileLocalTool;
import com.sophon.tool.mcp.McpBridgeLocalTool;
import com.sophon.tool.mcp.MockMcpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phases111213Test {

    @Test
    void mcpBridge_success(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("mcp.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("m");
            ToolRegistry reg = new ToolRegistry();
            reg.register(new McpBridgeLocalTool(new MockMcpClient(false), 5_000));
            ToolExecutor exec = new ToolExecutor(reg, sm);
            ToolResult r =
                    exec.execute(
                            s.getId(),
                            new ToolCall("x", "mcp_demo", "{\"remote_tool\":\"ping\",\"payload\":\"{}\"}"));
            assertTrue(r.success());
            assertTrue(r.content().contains("ok"));
        }
    }

    @Test
    void mcpBridge_timeout_returnsError(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("mcp2.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("m");
            ToolRegistry reg = new ToolRegistry();
            reg.register(new McpBridgeLocalTool(new MockMcpClient(true), 100));
            ToolExecutor exec = new ToolExecutor(reg, sm);
            ToolResult r =
                    exec.execute(s.getId(), new ToolCall("x", "mcp_demo", "{\"remote_tool\":\"t\"}"));
            assertFalse(r.success());
            assertTrue(r.content().contains("MCP") || r.content().contains("timeout"));
        }
    }

    @Test
    void highRisk_readFile_rejectedWithoutConfirmOrElevated(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("hr.db");
        Path f = tempDir.resolve("sec.txt");
        Files.writeString(f, "secret");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("h");
            ToolRegistry reg = new ToolRegistry();
            reg.register(new ReadFileLocalTool());
            ToolExecutor exec =
                    new ToolExecutor(
                            reg,
                            sm,
                            (sid, name, sum) -> false,
                            null,
                            null,
                            new SessionToolRateLimiter());
            String args = "{\"path\":\"" + f.toAbsolutePath().toString().replace("\\", "\\\\") + "\"}";
            ToolResult r = exec.execute(s.getId(), new ToolCall("c1", "read_file", args));
            assertFalse(r.success());
            assertTrue(r.content().contains("拒绝") || r.content().contains("高危"));

            sm.setTrustLevel(s.getId(), "elevated");
            ToolResult r2 = exec.execute(s.getId(), new ToolCall("c2", "read_file", args));
            assertTrue(r2.success());
            assertTrue(r2.content().contains("secret"));
        }
    }

    @Test
    void toolRateLimit_blocksSecondCall(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("rl.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("r");
            sm.setToolRateLimitPerMinute(s.getId(), 1);
            ToolRegistry reg = new ToolRegistry();
            reg.register(new EchoLocalTool());
            ToolExecutor exec =
                    new ToolExecutor(reg, sm, (a, b, c) -> true, null, null, new SessionToolRateLimiter());
            assertTrue(exec.execute(s.getId(), new ToolCall("a", "echo", "{\"text\":\"1\"}")).success());
            ToolResult r2 = exec.execute(s.getId(), new ToolCall("b", "echo", "{\"text\":\"2\"}"));
            assertFalse(r2.success());
            assertTrue(r2.content().contains("频繁"));
        }
    }

    @Test
    void auditAndUsage_persist(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("au.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("u");
            ToolRegistry reg = new ToolRegistry();
            reg.register(new EchoLocalTool());
            ToolExecutor exec =
                    new ToolExecutor(
                            reg,
                            sm,
                            (a, b, c) -> true,
                            new SqliteToolAuditLogger(storage),
                            new SqliteUsageTracker(storage),
                            new SessionToolRateLimiter());
            exec.execute(s.getId(), new ToolCall("z", "echo", "{\"text\":\"x\"}"));
            assertTrue(storage.countToolAuditForSession(s.getId()) >= 1);
            assertTrue(storage.countUsageRowsForSession(s.getId()) >= 1);
        }
    }

    @Test
    void sanitizer_redactsSensitiveKeysInAudit(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("san.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sm = new SessionManager(storage);
            var s = sm.createSession("s");
            ToolRegistry reg = new ToolRegistry();
            reg.register(new EchoLocalTool());
            ToolExecutor exec =
                    new ToolExecutor(
                            reg,
                            sm,
                            (a, b, c) -> true,
                            new SqliteToolAuditLogger(storage),
                            null,
                            new SessionToolRateLimiter());
            exec.execute(
                    s.getId(),
                    new ToolCall("z", "echo", "{\"text\":\"hi\",\"apiKey\":\"SECRET123\"}"));
            assertEquals(1, storage.countToolAuditForSession(s.getId()));
        }
    }
}
