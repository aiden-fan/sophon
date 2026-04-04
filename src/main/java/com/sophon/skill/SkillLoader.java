package com.sophon.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.tool.ToolRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 从 {@code skill.yaml} 或 {@code SKILL.md}（YAML frontmatter）加载 {@link SkillDefinition}，并对
 * {@link ToolRegistry} 做依赖校验。
 */
public final class SkillLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** SKILL.md：首个 {@code ---}…{@code ---} 块为 YAML frontmatter（与 Cursor 技能目录规范一致）。 */
    private static final Pattern SKILL_MD_FRONTMATTER =
            Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);

    public SkillDefinition loadYaml(Path path) throws IOException {
        return YAML.readValue(path.toFile(), SkillDefinition.class);
    }

    /** 从 classpath 加载 {@code skill.yaml}。 */
    public SkillDefinition loadYamlResource(ClassLoader classLoader, String classpathLocation)
            throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalArgumentException("找不到 classpath 资源: " + classpathLocation);
            }
            return YAML.readValue(in, SkillDefinition.class);
        }
    }

    /** 从文件读取 {@code SKILL.md} 并解析 frontmatter。 */
    public SkillDefinition loadSkillMd(Path path) throws IOException {
        return loadSkillMd(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** 从 classpath 加载 {@code SKILL.md}（解析 YAML frontmatter）。 */
    public SkillDefinition loadSkillMdResource(ClassLoader classLoader, String classpathLocation)
            throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalArgumentException("找不到 classpath 资源: " + classpathLocation);
            }
            return loadSkillMd(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * 解析 SKILL.md 全文：须以 {@code ---} 开头的 YAML frontmatter，字段与 {@code skill.yaml} 对齐；并兼容
     * Cursor 的 {@code name}（连字符）作为技能 id 的规范化形式（{@code -} → {@code _}）。
     */
    public SkillDefinition loadSkillMd(String markdown) throws IOException {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("SKILL.md 内容为空");
        }
        String text = markdown.stripLeading();
        var m = SKILL_MD_FRONTMATTER.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException(
                    "SKILL.md 须以 YAML frontmatter 开头：第一行为 ---，且包含闭合的 --- 行（见 skills/*/SKILL.md 示例）");
        }
        String fm = m.group(1).trim();
        JsonNode root = YAML.readTree(fm);

        String explicitId = text(root, "id");
        String nameField = text(root, "name");
        String id =
                explicitId != null && !explicitId.isBlank()
                        ? explicitId.trim()
                        : normalizeSkillId(nameField);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 需包含非空的 name 或 id");
        }

        String title = text(root, "title");
        String displayName =
                title != null && !title.isBlank()
                        ? title.trim()
                        : (nameField != null && !nameField.isBlank() ? nameField.trim() : id);

        String description = text(root, "description");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 需包含非空的 description（与 Cursor 规范一致）");
        }

        String minSophonVersion = text(root, "minSophonVersion");
        List<String> requiredTools = new ArrayList<>();
        JsonNode toolsNode = root.get("requiredTools");
        if (toolsNode != null && toolsNode.isArray()) {
            for (JsonNode n : toolsNode) {
                if (n != null && n.isTextual() && !n.asText().isBlank()) {
                    requiredTools.add(n.asText().trim());
                }
            }
        }

        return new SkillDefinition(id, displayName, description.trim(), minSophonVersion, requiredTools);
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull() || !n.isTextual()) {
            return null;
        }
        String s = n.asText();
        return s == null ? null : s;
    }

    /** 将 Cursor 风格的 {@code name}（如 {@code markdown-doc}）规范为 Sophon 技能 id（{@code markdown_doc}）。 */
    static String normalizeSkillId(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim().toLowerCase().replace('-', '_');
    }

    /** 校验 {@code requiredTools} 均在全局注册表中。 */
    public void validateRequiredTools(SkillDefinition def, ToolRegistry tools) {
        if (def.getRequiredTools() == null) {
            return;
        }
        for (String toolName : def.getRequiredTools()) {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalStateException("技能 " + def.getId() + " 声明了空的工具依赖");
            }
            if (tools.get(toolName.trim()).isEmpty()) {
                throw new IllegalStateException("技能 " + def.getId() + " 依赖的工具未注册: " + toolName);
            }
        }
    }
}
