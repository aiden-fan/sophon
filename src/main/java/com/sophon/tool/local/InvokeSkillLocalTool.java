package com.sophon.tool.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophon.skill.SkillExecutor;
import com.sophon.skill.SkillResult;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionContext;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolResult;
import com.sophon.tool.ToolRisk;

import java.util.List;
import java.util.Map;

/**
 * 供模型调用的技能入口：通过 {@link SkillExecutor} 执行已注册技能（如 {@code markdown_doc}）。
 */
public final class InvokeSkillLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolDefinition definition;
    private final SkillExecutor skillExecutor;
    private final List<String> registeredSkillIds;
    private final Map<String, String> registeredSkillDescriptions;

    public InvokeSkillLocalTool(SkillExecutor skillExecutor) {
        this(skillExecutor, List.of(), Map.of());
    }

    public InvokeSkillLocalTool(SkillExecutor skillExecutor, List<String> registeredSkillIds) {
        this(skillExecutor, registeredSkillIds, Map.of());
    }

    public InvokeSkillLocalTool(
            SkillExecutor skillExecutor,
            List<String> registeredSkillIds,
            Map<String, String> skillDescriptions) {
        this.skillExecutor = skillExecutor;
        List<String> ids = registeredSkillIds == null ? List.of() : registeredSkillIds.stream().filter(s -> s != null && !s.isBlank()).toList();
        this.registeredSkillIds = ids;
        this.registeredSkillDescriptions = skillDescriptions == null ? Map.of() : Map.copyOf(skillDescriptions);

        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode skillId = props.putObject("skill_id");
        skillId.put("type", "string");
        if (!ids.isEmpty()) {
            ArrayNode enumArr = skillId.putArray("enum");
            for (String id : ids) {
                enumArr.add(id);
            }
            skillId.put("description", "技能 id，可选值: " + String.join(", ", describeSkills(ids)));
        } else {
            skillId.put("description", "技能 id");
        }
        props.putObject("arguments").put("description", "传给技能的 JSON 对象（如 operation、path、content）");
        ArrayNode required = schema.putArray("required");
        required.add("skill_id");

        String desc =
                ids.isEmpty()
                        ? "调用已注册 Skill，按 skill_id 与 arguments 执行。"
                        : "调用已注册 Skill（" + String.join(", ", describeSkills(ids)) + "），按 skill_id 与 arguments 执行。";
        this.definition =
                new ToolDefinition(
                        "invoke_skill",
                        desc,
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
        String skillIdRaw = skillId.trim();
        String skillIdNormalized = normalizeSkillId(skillIdRaw);
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
        SkillResult sr = skillExecutor.run(sessionId, skillIdRaw, argsForSkill);
        if (!sr.success() && sr.message() != null && sr.message().contains("未知技能")) {
            if (!skillIdNormalized.equals(skillIdRaw)) {
                sr = skillExecutor.run(sessionId, skillIdNormalized, argsForSkill);
            }
        }
        if (!sr.success() && sr.message() != null && sr.message().contains("未知技能") && !registeredSkillIds.isEmpty()) {
            return ToolResult.error(
                    sr.message()
                            + "。可用技能: "
                            + String.join(", ", describeSkills(registeredSkillIds))
                            + "（连字符可自动映射为下划线）");
        }
        if (sr.success()) {
            return ToolResult.ok(sr.message());
        }
        return ToolResult.error(sr.message());
    }

    private static String normalizeSkillId(String skillId) {
        return skillId.trim().toLowerCase().replace('-', '_');
    }

    private List<String> describeSkills(List<String> ids) {
        return ids.stream()
                .map(
                        id -> {
                            String d = registeredSkillDescriptions.getOrDefault(id, "").trim();
                            if (d.isEmpty()) {
                                return id;
                            }
                            return id + "(" + d + ")";
                        })
                .toList();
    }
}
