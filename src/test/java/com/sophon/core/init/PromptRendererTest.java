package com.sophon.core.init;

import com.sophon.core.tool.NovelProjectPath;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class PromptRendererTest {

    @Test
    public void shouldReplaceUnknownPlaceholderWithDefaultMarker() throws Exception {
        Path root = Files.createTempDirectory("sophon-prompt-test");
        Files.writeString(root.resolve("novel.yaml"), "title: t");
        NovelProjectPath projectPath = new NovelProjectPath(root);
        PromptRenderer renderer = new PromptRenderer(projectPath, Map.of());

        String rendered = renderer.resolve("A=${unknown_var}");
        assertTrue(rendered.contains("(未提供:unknown_var)"));
    }
}
