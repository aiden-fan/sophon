package com.sophon.tool.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.skill.SkillExecutor;
import com.sophon.skill.SkillResult;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionContext;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolResult;
import com.sophon.tool.ToolRisk;

/**
 * 供模型调用的技能入口：通过 {@link SkillExecutor} 执行已注册技能（如 {@code markdown_doc}）。
 */
public final class InvokeSkillLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolDefinition definition;
    private final SkillExecutor skillExecutor;

    public InvokeSkillLocalTool(SkillExecutor skillExecutor) {
        this.skillExecutor = skillExecutor;
        JsonNode schema;
        try {
            schema =
                    JSON.readTree(
                            """
                            {
                              "type": "object",
                              "properties": {
                                "skill_id": {
                                  "type": "string",
                                  "description": "技能 id，例如 markdown_doc"
                                },
                                "arguments": {
                                  "description": "传给技能的 JSON 对象（如 operation、path、content）"
                                }
                              },
                              "required": ["skill_id"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "invoke_skill",
                        "调用已注册 Skill。生成 Markdown 文档请用 skill_id=markdown_doc，并在 arguments 中传 operation/path/content 等。",
                        schema,
                        ToolRisk.STANDARD);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws ToolExecutionException {
        String sessionId =
                ToolExecutionContext.currentSessionId()
                        .orElseThrow(() -> new ToolExecutionException("invoke_skill 需要在会话上下文中调用"));
        JsonNode root;
        try {
            root = JSON.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("参数不是合法 JSON: " + argumentsJson, e);
        }
        String skillId = root.path("skill_id").asText(null);
        if (skillId == null || skillId.isBlank()) {
            return ToolResult.error("缺少 skill_id");
        }
        String argsForSkill;
        try {
            JsonNode argNode = root.path("arguments");
            if (argNode.isMissingNode() || argNode.isNull()) {
                argsForSkill = "{}";
            } else if (argNode.isTextual()) {
                argsForSkill = argNode.asText();
            } else if (argNode.isObject()) {
                argsForSkill = JSON.writeValueAsString(argNode);
            } else {
                argsForSkill = argNode.toString();
            }
        } catch (Exception e) {
            throw new ToolExecutionException("无法解析 arguments: " + e.getMessage(), e);
        }
        SkillResult sr = skillExecutor.run(sessionId, skillId.trim(), argsForSkill);
        if (sr.success()) {
            return ToolResult.ok(sr.message());
        }
        return ToolResult.error(sr.message());
    }
}
