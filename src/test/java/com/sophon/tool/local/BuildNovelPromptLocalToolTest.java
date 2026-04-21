package com.sophon.tool.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildNovelPromptLocalToolTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void buildsPromptFromWorkspaceDocs(@TempDir Path tmp) throws Exception {
        Path ws = tmp.resolve("novel");
        Files.createDirectories(ws.resolve("prompts/tasks"));
        Files.createDirectories(ws.resolve("story/characters"));
        Files.createDirectories(ws.resolve("story/chapter_outlines"));
        Files.writeString(ws.resolve("prompts/base.md"), "你是网文助手。");
        Files.writeString(
                ws.resolve("prompts/tasks/chapter.md"),
                "参考世界观：${world_setting}\n角色材料：${characters}\n章节窗口：${chapter_outlines}\n需求：${user_request}");
        Files.writeString(ws.resolve("story/outline.md"), "主线：升级复仇。");
        Files.writeString(ws.resolve("story/world.md"), "世界：灵气复苏。");
        Files.writeString(ws.resolve("story/characters/林夜.md"), "角色：林夜。");
        Files.writeString(ws.resolve("story/chapter_outlines/第1章.md"), "第1章：出场。");
        Files.writeString(ws.resolve("story/chapter_outlines/第2章.md"), "第2章：冲突。");
        Files.writeString(ws.resolve("story/chapter_outlines/第4章.md"), "第4章：追击。");
        Files.writeString(ws.resolve("story/chapter_outlines/第7章.md"), "第7章：远景。");

        BuildNovelPromptLocalTool tool = new BuildNovelPromptLocalTool();
        ToolResult r =
                tool.execute(
                        JSON.writeValueAsString(
                                Map.of(
                                        "workspace_dir",
                                        ws.toString(),
                                        "task_type",
                                        "chapter",
                                        "user_request",
                                        "写第三章",
                                        "chapter_no",
                                        3,
                                        "character_names",
                                        List.of("林夜"))));
        assertTrue(r.success(), r.content());
        assertTrue(r.content().contains("你是网文助手"));
        assertTrue(r.content().contains("世界：灵气复苏"));
        assertTrue(r.content().contains("角色：林夜"));
        assertTrue(r.content().contains("第2章：冲突"));
        assertTrue(r.content().contains("第4章：追击"));
        assertTrue(r.content().contains("需求：写第三章"));
        assertFalse(r.content().contains("第7章：远景"));
        assertFalse(r.content().contains("${world_setting}"));
    }

    @Test
    void rejectsPathEscape(@TempDir Path tmp) throws Exception {
        Path ws = tmp.resolve("novel");
        Files.createDirectories(ws.resolve("prompts/tasks"));
        Files.writeString(ws.resolve("prompts/base.md"), "base");
        Files.writeString(ws.resolve("prompts/tasks/outline.md"), "outline");
        BuildNovelPromptLocalTool tool = new BuildNovelPromptLocalTool();
        assertThrows(
                com.sophon.tool.ToolExecutionException.class,
                () ->
                        tool.execute(
                                JSON.writeValueAsString(
                                        Map.of(
                                                "workspace_dir",
                                                ws.toString(),
                                                "task_type",
                                                "outline",
                                                "user_request",
                                                "写大纲",
                                                "extra_context_paths",
                                                List.of("../secret.md")))));
    }

    // workspace_dir 可选：未传时默认使用 user.dir，相关行为在集成流程中覆盖。
}
