package com.sophon.skill.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.ai.dto.ToolCall;
import com.sophon.skill.Skill;
import com.sophon.skill.SkillInvocation;
import com.sophon.skill.SkillResult;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.tool.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 小说写作编排技能：构建写作 prompt、读取上下文、并将状态回写到用户工作目录。
 */
public final class NovelWriterSkill implements Skill {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SkillDefinition definition;

    public NovelWriterSkill(SkillDefinition definition) {
        this.definition = definition;
    }

    @Override
    public SkillDefinition definition() {
        return definition;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation) {
        JsonNode root;
        try {
            root = JSON.readTree(invocation.argumentsJson() == null ? "{}" : invocation.argumentsJson());
        } catch (Exception e) {
            return SkillResult.error("参数需为 JSON，例如 {\"operation\":\"build_prompt\",...}");
        }

        String op = root.path("operation").asText("build_prompt").trim().toLowerCase();
        try {
            return switch (op) {
                case "build_prompt" -> buildPrompt(invocation, root);
                case "read_state" -> readState(invocation, root);
                case "sync_state" -> syncState(invocation, root);
                case "create_character" -> createCharacter(invocation, root);
                default -> SkillResult.error("未知 operation，请使用 build_prompt、read_state、sync_state 或 create_character");
            };
        } catch (Exception e) {
            return SkillResult.error(e.getMessage());
        }
    }

    private SkillResult buildPrompt(SkillInvocation invocation, JsonNode root) throws Exception {
        String taskType = root.path("task_type").asText("").trim();
        String userRequest = root.path("user_request").asText("").trim();
        if (taskType.isEmpty() || userRequest.isEmpty()) {
            return SkillResult.error("build_prompt 需要 task_type 与 user_request");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        var fields = root.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            if ("operation".equals(e.getKey())) {
                continue;
            }
            args.put(e.getKey(), e.getValue());
        }
        return callTool(invocation, "novel-build", "build_novel_prompt", args);
    }

    private SkillResult readState(SkillInvocation invocation, JsonNode root) throws Exception {
        String path = root.path("path").asText("").trim();
        if (path.isEmpty()) {
            return SkillResult.error("read_state 需要 path");
        }
        return callTool(invocation, "novel-read", "read_file", Map.of("path", path));
    }

    private SkillResult syncState(SkillInvocation invocation, JsonNode root) throws Exception {
        String path = root.path("path").asText("").trim();
        if (path.isEmpty()) {
            return SkillResult.error("sync_state 需要 path");
        }
        String content = root.path("content").asText("");
        boolean createParents = root.path("create_parents").asBoolean(false);
        boolean append = root.path("append").asBoolean(false);
        boolean createOnly = root.path("create_only").asBoolean(false);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        args.put("content", content);
        args.put("create_parents", createParents);
        if (createOnly) {
            return callTool(invocation, "novel-create", "create_file", args);
        }
        args.put("append", append);
        return callTool(invocation, "novel-write", "write_file", args);
    }

    /**
     * 兼容操作：接收模型常见参数 {@code character_name/output_path/content/prompt} 并写入角色卡文件。
     * <p>若未给 content，则回退使用 prompt；路径默认 {@code story/characters/<角色名>.md}。
     */
    private SkillResult createCharacter(SkillInvocation invocation, JsonNode root) throws Exception {
        String characterName = root.path("character_name").asText("").trim();
        if (characterName.isEmpty()) {
            return SkillResult.error("create_character 需要 character_name");
        }
        String outputPath = root.path("output_path").asText("").trim();
        if (outputPath.isEmpty()) {
            outputPath = "story/characters/" + characterName + ".md";
        }
        String content = root.path("content").asText("");
        if (content == null || content.isBlank()) {
            content = root.path("prompt").asText("");
        }
        if (content == null || content.isBlank()) {
            content = "# " + characterName + "角色卡\n\n（待补充）\n";
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", outputPath);
        args.put("content", content);
        args.put("create_parents", true);
        args.put("append", false);
        return callTool(invocation, "novel-character", "write_file", args);
    }

    private SkillResult callTool(
            SkillInvocation invocation, String idPrefix, String toolName, Map<String, Object> args)
            throws Exception {
        String argumentsJson = JSON.writeValueAsString(args);
        ToolCall call = new ToolCall(idPrefix + "-" + UUID.randomUUID(), toolName, argumentsJson);
        ToolResult tr = invocation.toolExecutor().execute(invocation.sessionId(), call);
        if (tr.success()) {
            return SkillResult.ok(tr.content());
        }
        return SkillResult.error(tr.content());
    }
}
