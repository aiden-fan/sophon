package com.sophon.core.tool;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class NovelProjectPathTest {

    @Test
    public void shouldRejectPathOutsideProject() throws Exception {
        Path root = Files.createTempDirectory("sophon-path-test");
        NovelProjectPath projectPath = new NovelProjectPath(root);

        try {
            projectPath.resolveInsideProject("../outside.txt");
            fail("expected illegal argument");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    @Test
    public void shouldScanNestedDocuments() throws Exception {
        Path root = Files.createTempDirectory("sophon-scan-test");
        Files.createDirectories(root.resolve("characters/group"));
        Files.createDirectories(root.resolve("outlines/arc1"));
        Files.createDirectories(root.resolve("chapters/partA"));
        Files.writeString(root.resolve("characters/group/张三.md"), "# 张三");
        Files.writeString(root.resolve("outlines/arc1/chapter-001.md"), "# 大纲");
        Files.writeString(root.resolve("chapters/partA/chapter-001-测试.txt"), "正文");

        NovelProjectPath projectPath = new NovelProjectPath(root);
        List<String> docs = projectPath.scanAllDocuments();

        assertTrue(docs.contains("characters/group/张三.md"));
        assertTrue(docs.contains("outlines/arc1/chapter-001.md"));
        assertTrue(docs.contains("chapters/partA/chapter-001-测试.txt"));
    }
}
