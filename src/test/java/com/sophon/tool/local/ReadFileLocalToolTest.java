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

class ReadFileLocalToolTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsUtf8File(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("a.txt");
        Files.writeString(f, "hello 世界");
        ReadFileLocalTool tool = new ReadFileLocalTool();
        String args = JSON.writeValueAsString(Map.of("path", f.toAbsolutePath().toString()));
        ToolResult r = tool.execute(args);
        assertTrue(r.success());
        assertEquals("hello 世界", r.content());
    }

    @Test
    void rejectsMissingFile(@TempDir Path dir) throws Exception {
        ReadFileLocalTool tool = new ReadFileLocalTool();
        String args = JSON.writeValueAsString(Map.of("path", dir.resolve("nope.txt").toString()));
        ToolResult r = tool.execute(args);
        assertFalse(r.success());
        assertTrue(r.content().contains("不存在"));
    }

    @Test
    void rejectsOversize(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("big.bin");
        Files.write(f, new byte[20_000]);
        ReadFileLocalTool tool = new ReadFileLocalTool(10_000);
        String args = JSON.writeValueAsString(Map.of("path", f.toAbsolutePath().toString()));
        ToolResult r = tool.execute(args);
        assertFalse(r.success());
        assertTrue(r.content().contains("过大"));
    }
}
