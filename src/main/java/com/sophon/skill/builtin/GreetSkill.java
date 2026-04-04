package com.sophon.skill.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.ai.dto.ToolCall;
import com.sophon.skill.Skill;
import com.sophon.skill.SkillInvocation;
import com.sophon.skill.SkillResult;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.tool.ToolResult;

import java.util.Map;
import java.util.UUID;

/**
 * 内置示例技能：通过 {@code echo} 工具问候；参数 JSON：{@code {"name":"世界"}}。
 */
public final class GreetSkill implements Skill {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SkillDefinition definition;

    public GreetSkill(SkillDefinition definition) {
        this.definition = definition;
    }

    @Override
    public SkillDefinition definition() {
        return definition;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation) {
        String name = "there";
        try {
            JsonNode root = JSON.readTree(invocation.argumentsJson() == null ? "{}" : invocation.argumentsJson());
            if (root.hasNonNull("name") && !root.path("name").asText().isBlank()) {
                name = root.path("name").asText().trim();
            }
        } catch (Exception e) {
            return SkillResult.error("参数需为 JSON，例如 {\"name\":\"Sophon\"}");
        }
        try {
            String echoArgs = JSON.writeValueAsString(Map.of("text", "Hello, " + name));
            ToolCall call = new ToolCall("skill-" + UUID.randomUUID(), "echo", echoArgs);
            ToolResult tr = invocation.toolExecutor().execute(invocation.sessionId(), call);
            if (tr.success()) {
                return SkillResult.ok(tr.content());
            }
            return SkillResult.error(tr.content());
        } catch (Exception e) {
            return SkillResult.error(e.getMessage());
        }
    }
}
