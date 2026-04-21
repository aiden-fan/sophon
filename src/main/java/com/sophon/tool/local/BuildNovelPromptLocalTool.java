package com.sophon.tool.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolResult;
import com.sophon.tool.ToolRisk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据小说工作目录拼装完整写作提示词：
 * 基础 prompt + 任务模板 + 按需角色 + 章节窗口（前后 3 章）等文档片段 + 用户当前需求。
 */
public final class BuildNovelPromptLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_MAX_CHARS = 16_000;
    private static final int MAX_FILE_CHARS = 4_000;
    private static final Pattern PLACEHOLDER_DOLLAR = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_]+)\\s*}");
    private static final Pattern PLACEHOLDER_BRACES = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final ToolDefinition definition;

    public BuildNovelPromptLocalTool() {
        JsonNode schema;
        try {
            schema =
                    JSON.readTree(
                            """
                            {
                              "type": "object",
                              "properties": {
                                "workspace_dir": {
                                  "type": "string",
                                  "description": "小说工作目录（用户目录）；可选，默认 JVM 启动目录 user.dir"
                                },
                                "task_type": {
                                  "type": "string",
                                  "description": "任务类型：outline | character | chapter_outline | chapter | revise"
                                },
                                "user_request": {
                                  "type": "string",
                                  "description": "用户本轮需求原文"
                                },
                                "chapter_id": {
                                  "type": "string",
                                  "description": "章节标识（可选），例如 ch01"
                                },
                                "chapter_no": {
                                  "type": "integer",
                                  "description": "章节号（可选）；用于加载 story/chapter_outlines/第n章.md 及前后 3 章"
                                },
                                "character_names": {
                                  "type": "array",
                                  "items": {"type": "string"},
                                  "description": "本次任务涉及的角色名列表；按需加载 story/characters/<角色名>.md"
                                },
                                "extra_context_paths": {
                                  "type": "array",
                                  "items": {"type": "string"},
                                  "description": "需额外拼接的上下文文件（相对 workspace_dir 的相对路径）"
                                },
                                "max_chars": {
                                  "type": "integer",
                                  "description": "输出 prompt 最大字符数，默认 16000"
                                }
                              },
                              "required": ["task_type", "user_request"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "build_novel_prompt",
                        "按任务类型从小说工作目录拼装完整 prompt（基础模板+角色+大纲+章节上下文）。",
                        schema,
                        ToolRisk.STANDARD);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws ToolExecutionException {
        JsonNode args;
        try {
            args = JSON.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("参数不是合法 JSON: " + argumentsJson, e);
        }
        String taskType = args.path("task_type").asText("").trim().toLowerCase();
        String userRequest = args.path("user_request").asText("").trim();
        if (taskType.isEmpty()) {
            throw new ToolExecutionException("缺少 task_type");
        }
        if (userRequest.isEmpty()) {
            throw new ToolExecutionException("缺少 user_request");
        }
        String workspace = args.path("workspace_dir").asText("").trim();
        Path root = workspace.isEmpty() ? Path.of(System.getProperty("user.dir", ".")) : Path.of(workspace);
        root = root.normalize().toAbsolutePath();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return ToolResult.error("workspace_dir 不存在或不是目录: " + root);
        }
        String chapterId = args.path("chapter_id").asText("").trim();
        int chapterNo = args.path("chapter_no").asInt(0);
        if (("chapter".equals(taskType) || "chapter_outline".equals(taskType)) && chapterNo <= 0) {
            return ToolResult.error("task_type=" + taskType + " 需要提供 chapter_no");
        }
        int maxChars = Math.max(2_000, args.path("max_chars").asInt(DEFAULT_MAX_CHARS));
        maxChars = Math.min(50_000, maxChars);

        String basePath = "prompts/base.md";
        String taskPath = "prompts/tasks/" + taskType + ".md";
        String basePrompt = readOptional(root, basePath);
        String taskPromptRaw = readOptional(root, taskPath);
        if (taskPromptRaw.isBlank()) {
            return ToolResult.error("缺少任务模板: " + taskPath);
        }

        Set<String> placeholders = detectPlaceholders(basePrompt + "\n" + taskPromptRaw);
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put(basePath, basePrompt);
        sections.put(taskPath, taskPromptRaw);

        List<String> readOrder = new ArrayList<>();
        if (needsAny(placeholders, "story_outline", "outline")) {
            readOrder.add("story/outline.md");
        }
        if (needsAny(placeholders, "world_setting", "world")) {
            readOrder.add("story/world.md");
        }
        if (needsAny(placeholders, "style", "style_guide")) {
            readOrder.add("story/style.md");
        }
        if (needsAny(placeholders, "characters", "character_profiles")) {
            addCharacterFiles(readOrder, args.path("character_names"));
        }
        if (needsAny(placeholders, "chapter_outlines", "chapter_outline_window")) {
            addChapterOutlineWindow(readOrder, chapterNo);
        }
        if (needsAny(placeholders, "current_chapter", "chapter_content")) {
            if (!chapterId.isEmpty()) {
                readOrder.add("chapters/" + chapterId + ".md");
            } else if (chapterNo > 0) {
                readOrder.add("chapters/第" + chapterNo + "章.md");
            }
        }
        JsonNode extraPaths = args.path("extra_context_paths");
        if (extraPaths.isArray()) {
            for (JsonNode n : extraPaths) {
                if (n != null && n.isTextual() && !n.asText().isBlank()) {
                    readOrder.add(n.asText().trim());
                }
            }
        }
        for (String relative : readOrder) {
            String c = readOptional(root, relative);
            if (!c.isBlank()) {
                sections.put(relative, c);
            }
        }

        String characters = joinByPrefix(sections, "story/characters/");
        String chapterOutlines = joinByPrefix(sections, "story/chapter_outlines/");
        String currentChapter =
                !chapterId.isEmpty()
                        ? sections.getOrDefault("chapters/" + chapterId + ".md", "")
                        : sections.getOrDefault("chapters/第" + chapterNo + "章.md", "");
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("task_type", taskType);
        replacements.put("user_request", userRequest);
        replacements.put("chapter_id", chapterId);
        replacements.put("chapter_no", chapterNo > 0 ? String.valueOf(chapterNo) : "");
        replacements.put("characters", characters);
        replacements.put("character_profiles", characters);
        replacements.put("story_outline", sections.getOrDefault("story/outline.md", ""));
        replacements.put("outline", sections.getOrDefault("story/outline.md", ""));
        replacements.put("world_setting", sections.getOrDefault("story/world.md", ""));
        replacements.put("world", sections.getOrDefault("story/world.md", ""));
        replacements.put("style", sections.getOrDefault("story/style.md", ""));
        replacements.put("style_guide", sections.getOrDefault("story/style.md", ""));
        replacements.put("chapter_outlines", chapterOutlines);
        replacements.put("chapter_outline_window", chapterOutlines);
        replacements.put("current_chapter", currentChapter);
        replacements.put("chapter_content", currentChapter);
        replacements.put("context_docs", renderContextDocs(sections));
        String baseRendered = renderTemplate(basePrompt, replacements);
        String taskRendered = renderTemplate(taskPromptRaw, replacements);

        List<String> blocks = new ArrayList<>();
        blocks.add("# 小说创作执行提示词");
        blocks.add(renderTaskMeta(taskType, userRequest, chapterId, chapterNo, args.path("character_names")));
        if (!baseRendered.isBlank()) {
            blocks.add("## 基础 Prompt（prompts/base.md）\n" + baseRendered);
        }
        if (!taskRendered.isBlank()) {
            blocks.add("## 任务 Prompt（prompts/tasks/" + taskType + ".md）\n" + taskRendered);
        }
        blocks.add(renderLoadedDocList(sections));
        blocks.add("## 输出要求\n" + outputRequirement(taskType).stripTrailing());
        blocks.add(
                """
                ## 状态同步要求
                - 若本轮内容导致角色设定/世界观/章节计划变更，请在完成正文后同步更新对应文件。
                - 新建文件优先用 create_file；覆盖更新用 write_file；必要时先 read_file 再改写。
                """.stripTrailing());
        return ToolResult.ok(joinAndCap(blocks, maxChars));
    }

    private static Set<String> detectPlaceholders(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m1 = PLACEHOLDER_DOLLAR.matcher(text == null ? "" : text);
        while (m1.find()) {
            out.add(m1.group(1));
        }
        Matcher m2 = PLACEHOLDER_BRACES.matcher(text == null ? "" : text);
        while (m2.find()) {
            out.add(m2.group(1));
        }
        return out;
    }

    private static boolean needsAny(Set<String> placeholders, String... names) {
        for (String n : names) {
            if (placeholders.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String renderTemplate(String template, Map<String, String> replacements) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue();
            out = out.replace("${" + e.getKey() + "}", val);
            out = out.replace("{{" + e.getKey() + "}}", val);
        }
        return out;
    }

    private static String readOptional(Path root, String relativePath) throws ToolExecutionException {
        Path p = safeResolveInsideRoot(root, relativePath);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            return "";
        }
        return readCapped(p, MAX_FILE_CHARS);
    }

    private static String joinByPrefix(Map<String, String> sections, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sections.entrySet()) {
            if (!e.getKey().startsWith(prefix) || e.getValue().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("[").append(e.getKey()).append("]\n").append(e.getValue());
        }
        return sb.toString();
    }

    private static String renderContextDocs(Map<String, String> sections) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sections.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("### ").append(e.getKey()).append("\n").append(e.getValue());
        }
        return sb.toString();
    }

    private static void addCharacterFiles(List<String> readOrder, JsonNode namesNode) {
        if (!namesNode.isArray()) {
            return;
        }
        TreeSet<String> dedup = new TreeSet<>();
        for (JsonNode n : namesNode) {
            if (n == null || !n.isTextual()) {
                continue;
            }
            String name = n.asText("").trim();
            if (name.isEmpty()) {
                continue;
            }
            dedup.add(name);
        }
        for (String name : dedup) {
            readOrder.add("story/characters/" + name + ".md");
        }
    }

    private static void addChapterOutlineWindow(List<String> readOrder, int centerChapter) {
        if (centerChapter <= 0) {
            return;
        }
        int from = Math.max(1, centerChapter - 3);
        int to = centerChapter + 3;
        for (int n = from; n <= to; n++) {
            readOrder.add("story/chapter_outlines/第" + n + "章.md");
        }
    }

    private static String renderTaskMeta(
            String taskType, String userRequest, String chapterId, int chapterNo, JsonNode namesNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 当前任务\n");
        sb.append("- task_type: ").append(taskType).append('\n');
        sb.append("- 用户需求: ").append(userRequest).append('\n');
        if (!chapterId.isEmpty()) {
            sb.append("- chapter_id: ").append(chapterId).append('\n');
        }
        if (chapterNo > 0) {
            sb.append("- chapter_no: ").append(chapterNo).append('\n');
        }
        if (!namesNode.isArray()) {
            return sb.toString().stripTrailing();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode n : namesNode) {
            if (n != null && n.isTextual() && !n.asText().isBlank()) {
                names.add(n.asText().trim());
            }
        }
        if (!names.isEmpty()) {
            sb.append("- character_names: ").append(String.join(", ", names)).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String renderLoadedDocList(Map<String, String> sections) {
        StringBuilder sb = new StringBuilder("## 已加载文档\n");
        for (String name : sections.keySet()) {
            sb.append("- ").append(name).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static Path safeResolveInsideRoot(Path root, String relativePath) throws ToolExecutionException {
        Path relative = Path.of(relativePath).normalize();
        if (relative.isAbsolute()) {
            throw new ToolExecutionException("extra_context_paths 仅允许相对路径: " + relativePath);
        }
        Path resolved = root.resolve(relative).normalize().toAbsolutePath();
        if (!resolved.startsWith(root)) {
            throw new ToolExecutionException("路径越界（必须位于 workspace_dir 内）: " + relativePath);
        }
        return resolved;
    }

    private static String readCapped(Path file, int capChars) throws ToolExecutionException {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.length() <= capChars) {
                return text;
            }
            return text.substring(0, capChars) + "\n...[truncated]";
        } catch (Exception e) {
            throw new ToolExecutionException("读取失败: " + file + " — " + e.getMessage(), e);
        }
    }

    private static String joinAndCap(List<String> blocks, int maxChars) {
        String full = String.join("\n\n", blocks).stripTrailing() + "\n";
        if (full.length() <= maxChars) {
            return full;
        }
        return full.substring(0, maxChars) + "\n...[prompt truncated]";
    }

    private static String outputRequirement(String taskType) {
        return switch (taskType) {
            case "outline" -> "- 输出结构化大纲：世界观、主线、卷划分、冲突升级、关键伏笔与回收。\n";
            case "character" -> "- 输出角色卡：身份、动机、目标、成长弧、关系网、禁忌与反转点。\n";
            case "chapter_outline" -> "- 输出章节大纲：本章目标、冲突、关键场景、收束点、下一章钩子。\n";
            case "chapter" -> "- 输出章节正文：保证叙事连贯、人物口吻一致、结尾有推进。\n";
            case "revise" -> "- 输出改写版本并列出修改点：节奏、人物一致性、逻辑漏洞、文风统一。\n";
            default -> "- 按用户需求输出可直接落库的小说内容，并说明必要的状态变更建议。\n";
        };
    }
}
