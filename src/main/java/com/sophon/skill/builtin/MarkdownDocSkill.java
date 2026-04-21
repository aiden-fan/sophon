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
 * 生成 / 读取 Markdown 文档：通过 {@code create_file}（仅新建）、{@code write_file}（覆盖/追加）、{@code read_file}。
 * <p>参数 JSON 示例：<code>{"operation":"create","path":"notes/x.md","content":"# 标题\\n","create_parents":true}</code>、
 * <code>{"operation":"write",...}</code> 或 <code>{"operation":"read","path":"notes/x.md"}</code>。
 */
public final class MarkdownDocSkill implements Skill {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SkillDefinition definition;

    public MarkdownDocSkill(SkillDefinition definition) {
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
            return SkillResult.error("参数需为 JSON，例如 {\"operation\":\"write\",\"path\":\"a.md\",\"content\":\"# …\"}");
        }
        String op = normalizeOperation(root.path("operation").asText("write").trim().toLowerCase());
        String path = root.path("path").asText("").trim();
        if (path.isEmpty()) {
            return SkillResult.error("缺少 path");
        }
        try {
            if ("read".equals(op)) {
                String readArgs = JSON.writeValueAsString(Map.of("path", path));
                ToolCall call = new ToolCall("md-read-" + UUID.randomUUID(), "read_file", readArgs);
                ToolResult tr = invocation.toolExecutor().execute(invocation.sessionId(), call);
                return tr.success() ? SkillResult.ok(tr.content()) : SkillResult.error(tr.content());
            }
            if ("create".equals(op)) {
                String content = root.path("content").asText("");
                boolean createParents = root.path("create_parents").asBoolean(false);
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("path", path);
                cm.put("content", content);
                cm.put("create_parents", createParents);
                String createArgs = JSON.writeValueAsString(cm);
                ToolCall call = new ToolCall("md-create-" + UUID.randomUUID(), "create_file", createArgs);
                ToolResult tr = invocation.toolExecutor().execute(invocation.sessionId(), call);
                return tr.success() ? SkillResult.ok(tr.content()) : SkillResult.error(tr.content());
            }
            if ("write".equals(op) || "append".equals(op)) {
                String content = root.path("content").asText("");
                boolean append = "append".equals(op) || root.path("append").asBoolean(false);
                boolean createParents = root.path("create_parents").asBoolean(false);
                Map<String, Object> wm = new LinkedHashMap<>();
                wm.put("path", path);
                wm.put("content", content);
                wm.put("append", append);
                wm.put("create_parents", createParents);
                String writeArgs = JSON.writeValueAsString(wm);
                ToolCall call = new ToolCall("md-write-" + UUID.randomUUID(), "write_file", writeArgs);
                ToolResult tr = invocation.toolExecutor().execute(invocation.sessionId(), call);
                return tr.success() ? SkillResult.ok(tr.content()) : SkillResult.error(tr.content());
            }
            return SkillResult.error("未知 operation，请使用 create、write、append、read（兼容 create_file/write_file/read_file）");
        } catch (Exception e) {
            return SkillResult.error(e.getMessage());
        }
    }

    private static String normalizeOperation(String op) {
        if (op == null) {
            return "write";
        }
        return switch (op) {
            case "create_file" -> "create";
            case "write_file" -> "write";
            case "read_file" -> "read";
            default -> op;
        };
    }
}
