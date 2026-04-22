package com.sophon.core.tool.builtin;

import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolResult;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class UpdateStoryProgressToolTest {

    @Test
    public void writesStoryProgressFile() throws Exception {
        Path root = Files.createTempDirectory("sophon-sp");
        NovelProjectPath projectPath = new NovelProjectPath(root);
        var tool = new UpdateStoryProgressTool(projectPath);

        ToolResult r = tool.execute(Map.of("content", "---\ndescription: x\n---\n# 进展\n"));
        assertTrue(!r.isError());
        assertTrue(Files.exists(root.resolve("story-progress.md")));
        assertTrue(Files.readString(root.resolve("story-progress.md")).contains("进展"));
    }
}
