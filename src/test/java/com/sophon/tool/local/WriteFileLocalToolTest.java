package com.sophon.tool.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteFileLocalToolTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesUtf8(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("a.md");
        WriteFileLocalTool t = new WriteFileLocalTool();
        String args =
                JSON.writeValueAsString(Map.of("path", f.toString(), "content", "# 标题\n"));
        ToolResult r = t.execute(args);
        assertTrue(r.success());
        assertEquals("# 标题\n", Files.readString(f));
    }

    @Test
    void appends(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("b.md");
        Files.writeString(f, "A\n");
        WriteFileLocalTool t = new WriteFileLocalTool();
        String args =
                JSON.writeValueAsString(
                        Map.of("path", f.toString(), "content", "B", "append", true));
        ToolResult r = t.execute(args);
        assertTrue(r.success());
        assertTrue(Files.readString(f).contains("A"));
        assertTrue(Files.readString(f).contains("B"));
    }
}
