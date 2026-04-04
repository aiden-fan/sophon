package com.sophon.tool.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateFileLocalToolTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void createsEmptyWhenNoContent(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("e.md");
        CreateFileLocalTool t = new CreateFileLocalTool();
        ToolResult r = t.execute(JSON.writeValueAsString(Map.of("path", f.toString())));
        assertTrue(r.success());
        assertEquals("", Files.readString(f));
    }

    @Test
    void createsWithContent(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("c.md");
        CreateFileLocalTool t = new CreateFileLocalTool();
        ToolResult r =
                t.execute(
                        JSON.writeValueAsString(
                                Map.of("path", f.toString(), "content", "x", "create_parents", true)));
        assertTrue(r.success());
        assertEquals("x", Files.readString(f));
    }

    @Test
    void rejectsWhenExists(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("x.md");
        Files.writeString(f, "old");
        CreateFileLocalTool t = new CreateFileLocalTool();
        ToolResult r = t.execute(JSON.writeValueAsString(Map.of("path", f.toString(), "content", "new")));
        assertFalse(r.success());
        assertTrue(r.content().contains("已存在"));
        assertEquals("old", Files.readString(f));
    }
}
