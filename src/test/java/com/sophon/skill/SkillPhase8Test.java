package com.sophon.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.config.AiConfig;
import com.sophon.core.session.SessionManager;
import com.sophon.skill.builtin.GreetSkill;
import com.sophon.skill.builtin.MarkdownDocSkill;
import com.sophon.skill.builtin.NovelWriterSkill;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.BuildNovelPromptLocalTool;
import com.sophon.tool.local.EchoLocalTool;
import com.sophon.tool.local.CreateFileLocalTool;
import com.sophon.tool.local.ReadFileLocalTool;
import com.sophon.tool.local.WriteFileLocalTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPhase8Test {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void loadYaml_validatesRequiredTools(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("sk");
        Files.createDirectories(dir);
        Path yaml = dir.resolve("skill.yaml");
        Files.writeString(
                yaml,
                """
                id: bad
                name: Bad
                requiredTools:
                  - missing_tool
                """);
        SkillLoader loader = new SkillLoader();
        SkillDefinition def = loader.loadYaml(yaml);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoLocalTool());
        assertThrows(IllegalStateException.class, () -> loader.validateRequiredTools(def, tools));
    }

    @Test
    void greetSkill_executesViaEcho(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("s8.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/greet/SKILL.md");
            loader.validateRequiredTools(def, tools);
            reg.register(new GreetSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            SkillResult r = exec.run(s.getId(), "greet", "{\"name\":\"Test\"}");
            assertTrue(r.success());
            assertTrue(r.message().contains("Hello, Test"));
        }
    }

    @Test
    void greetSkill_rejectedWhenSkillDisabled(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("s8b.db");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            sessions.setSkillDisabled(s.getId(), "greet", true);
            AiConfig ai = new AiConfig();
            ai.setSystemPrompt("");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new EchoLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/greet/SKILL.md");
            reg.register(new GreetSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            SkillResult r = exec.run(s.getId(), "greet", "{}");
            assertTrue(r.message().contains("禁用"));
        }
    }

    @Test
    void markdownDocSkill_writesAndReads(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("mdskill.db");
        Path out = tempDir.resolve("out.md");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            sessions.setTrustLevel(s.getId(), "elevated");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new ReadFileLocalTool());
            tools.register(new CreateFileLocalTool());
            tools.register(new WriteFileLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/markdown_doc/SKILL.md");
            loader.validateRequiredTools(def, tools);
            reg.register(new MarkdownDocSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            String writeArgs =
                    JSON.writeValueAsString(
                            Map.of(
                                    "operation",
                                    "write",
                                    "path",
                                    out.toString(),
                                    "content",
                                    "# Hello",
                                    "create_parents",
                                    true));
            SkillResult w = exec.run(s.getId(), "markdown_doc", writeArgs);
            assertTrue(w.success(), w.message());
            assertEquals("# Hello", Files.readString(out).trim());
            String readArgs =
                    JSON.writeValueAsString(Map.of("operation", "read", "path", out.toString()));
            SkillResult r = exec.run(s.getId(), "markdown_doc", readArgs);
            assertTrue(r.success(), r.message());
            assertTrue(r.message().contains("Hello"));
        }
    }

    @Test
    void markdownDocSkill_createNewFileWithoutElevatedTrust(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("mdcreate.db");
        Path out = tempDir.resolve("new.md");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new ReadFileLocalTool());
            tools.register(new CreateFileLocalTool());
            tools.register(new WriteFileLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/markdown_doc/SKILL.md");
            loader.validateRequiredTools(def, tools);
            reg.register(new MarkdownDocSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            String createArgs =
                    JSON.writeValueAsString(
                            Map.of(
                                    "operation",
                                    "create",
                                    "path",
                                    out.toString(),
                                    "content",
                                    "# New",
                                    "create_parents",
                                    true));
            SkillResult c = exec.run(s.getId(), "markdown_doc", createArgs);
            assertTrue(c.success(), c.message());
            assertEquals("# New", Files.readString(out).trim());
        }
    }

    @Test
    void markdownDocSkill_acceptsOperationAliasCreateFile(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("mdalias.db");
        Path out = tempDir.resolve("alias.md");
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new ReadFileLocalTool());
            tools.register(new CreateFileLocalTool());
            tools.register(new WriteFileLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/markdown_doc/SKILL.md");
            loader.validateRequiredTools(def, tools);
            reg.register(new MarkdownDocSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            String createArgs =
                    JSON.writeValueAsString(
                            Map.of(
                                    "operation",
                                    "create_file",
                                    "path",
                                    out.toString(),
                                    "content",
                                    "# Alias",
                                    "create_parents",
                                    true));
            SkillResult c = exec.run(s.getId(), "markdown_doc", createArgs);
            assertTrue(c.success(), c.message());
            assertEquals("# Alias", Files.readString(out).trim());
        }
    }

    @Test
    void novelWriterSkill_buildPromptAndSyncState(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("novel.db");
        Path ws = tempDir.resolve("workspace");
        Files.createDirectories(ws.resolve("prompts/tasks"));
        Files.createDirectories(ws.resolve("story/characters"));
        Files.createDirectories(ws.resolve("story/chapter_outlines"));
        Files.writeString(ws.resolve("prompts/base.md"), "你是网文助手");
        Files.writeString(
                ws.resolve("prompts/tasks/chapter.md"),
                "世界：${world_setting}\n角色：${characters}\n窗口：${chapter_outlines}\n需求：${user_request}");
        Files.writeString(ws.resolve("story/outline.md"), "主线：复仇");
        Files.writeString(ws.resolve("story/world.md"), "世界：九州");
        Files.writeString(ws.resolve("story/characters/林夜.md"), "林夜：沉稳");
        Files.writeString(ws.resolve("story/chapter_outlines/第1章.md"), "第一章铺垫");
        Files.writeString(ws.resolve("story/chapter_outlines/第2章.md"), "第二章冲突");

        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            sessions.setTrustLevel(s.getId(), "elevated");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new BuildNovelPromptLocalTool());
            tools.register(new ReadFileLocalTool());
            tools.register(new CreateFileLocalTool());
            tools.register(new WriteFileLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/novel_writer/SKILL.md");
            loader.validateRequiredTools(def, tools);
            reg.register(new NovelWriterSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);

            String buildArgs =
                    JSON.writeValueAsString(
                            Map.of(
                                    "operation",
                                    "build_prompt",
                                    "workspace_dir",
                                    ws.toString(),
                                    "task_type",
                                    "chapter",
                                    "user_request",
                                    "写第一章",
                                    "chapter_no",
                                    2,
                                    "character_names",
                                    java.util.List.of("林夜")));
            SkillResult p = exec.run(s.getId(), "novel_writer", buildArgs);
            assertTrue(p.success(), p.message());
            assertTrue(p.message().contains("世界：九州"));
            assertTrue(p.message().contains("第二章冲突"));
            assertTrue(p.message().contains("林夜：沉稳"));

            Path charFile = ws.resolve("story/characters/林夜.md");
            String syncArgs =
                    JSON.writeValueAsString(
                            Map.of(
                                    "operation",
                                    "sync_state",
                                    "path",
                                    charFile.toString(),
                                    "content",
                                    "林夜：冷静",
                                    "create_only",
                                    false,
                                    "create_parents",
                                    true));
            SkillResult w = exec.run(s.getId(), "novel_writer", syncArgs);
            assertTrue(w.success(), w.message());
            assertTrue(Files.readString(charFile).contains("林夜"));
        }
    }

    @Test
    void novelWriterSkill_createCharacterCompatibility(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("novel2.db");
        Path ws = tempDir.resolve("workspace2");
        Files.createDirectories(ws.resolve("story/characters"));
        try (SQLiteStorage storage = new SQLiteStorage(db)) {
            storage.initialize();
            SessionManager sessions = new SessionManager(storage);
            var s = sessions.createSession("t");
            sessions.setTrustLevel(s.getId(), "elevated");
            ToolRegistry tools = new ToolRegistry();
            tools.register(new BuildNovelPromptLocalTool());
            tools.register(new ReadFileLocalTool());
            tools.register(new CreateFileLocalTool());
            tools.register(new WriteFileLocalTool());
            ToolExecutor toolExec = new ToolExecutor(tools, sessions);
            SkillRegistry reg = new SkillRegistry();
            SkillLoader loader = new SkillLoader();
            SkillDefinition def =
                    loader.loadSkillMdResource(getClass().getClassLoader(), "skills/novel_writer/SKILL.md");
            loader.validateRequiredTools(def, tools);
            reg.register(new NovelWriterSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            String args =
                    JSON.writeValueAsString(
                            Map.of(
                                    "operation",
                                    "create_character",
                                    "character_name",
                                    "李四",
                                    "output_path",
                                    ws.resolve("story/characters/李四.md").toString(),
                                    "content",
                                    "# 李四角色卡\\n- 职业：无业游民"));
            SkillResult r = exec.run(s.getId(), "novel_writer", args);
            assertTrue(r.success(), r.message());
            assertTrue(Files.readString(ws.resolve("story/characters/李四.md")).contains("李四角色卡"));
        }
    }
}
