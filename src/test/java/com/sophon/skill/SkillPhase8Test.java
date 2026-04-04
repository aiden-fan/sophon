package com.sophon.skill;

import com.sophon.config.AiConfig;
import com.sophon.core.session.SessionManager;
import com.sophon.skill.builtin.GreetSkill;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.EchoLocalTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPhase8Test {

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
            SkillDefinition def = loader.loadResource(getClass().getClassLoader(), "skills/greet/skill.yaml");
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
            SkillDefinition def = loader.loadResource(getClass().getClassLoader(), "skills/greet/skill.yaml");
            reg.register(new GreetSkill(def));
            SkillExecutor exec = new SkillExecutor(sessions, reg, toolExec);
            SkillResult r = exec.run(s.getId(), "greet", "{}");
            assertTrue(r.message().contains("禁用"));
        }
    }
}
