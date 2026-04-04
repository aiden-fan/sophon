package com.sophon.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.config.AiConfig;
import com.sophon.core.session.SessionManager;
import com.sophon.skill.builtin.GreetSkill;
import com.sophon.skill.builtin.MarkdownDocSkill;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
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
}
