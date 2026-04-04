package com.sophon.tool.local;

import com.sophon.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCommandLocalToolTest {

    @Test
    void echoesExitCodeAndOutput() throws Exception {
        RunCommandLocalTool t = new RunCommandLocalTool();
        ToolResult r = t.execute("{\"command\":[\"echo\",\"hello\"]}");
        assertTrue(r.success());
        assertTrue(r.content().contains("exitCode=0"));
        assertTrue(r.content().contains("hello"));
    }

    @Test
    void rejectsEmptyCommand() throws Exception {
        ToolResult r = new RunCommandLocalTool().execute("{}");
        assertFalse(r.success());
    }

    @Test
    void rejectsWorkingDirOutsideProcessBase() throws Exception {
        Path base = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path root = base.getRoot();
        if (root == null || base.equals(root) || !root.isAbsolute()) {
            return;
        }
        RunCommandLocalTool t = new RunCommandLocalTool();
        String rootStr = root.toString().replace("\\", "\\\\");
        ToolResult r = t.execute("{\"command\":[\"echo\",\"x\"],\"working_directory\":\"" + rootStr + "\"}");
        assertFalse(r.success());
        assertTrue(r.content().contains("工作目录"));
    }
}
