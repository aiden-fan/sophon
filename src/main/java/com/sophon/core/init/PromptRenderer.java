package com.sophon.core.init;

import com.sophon.core.tool.NovelProjectPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 加载 base prompt 并替换关键词为实际项目内容
 *
 * 关键词基于**已选中的文档**按需拼接，不会全量读取：
 * - ${world_setting}      - 选中了 world-setting.md 时才拼接
 * - ${outline}            - 选中了 outline.md 时才拼接
 * - ${characters}         - 仅拼接选中的 character 类型文档
 * - ${outline_chapter_N}  - 仅拼接选中的 outline 文档
 * - ${chapter_N}          - 仅拼接选中的 chapter 文档
 * - ${novel_info}         - 始终读取 outline.md 全文（含 frontmatter）
 * - ${story_progress}     - 始终读取 story-progress.md 全文（含 frontmatter）
 * - ${structure}          - 始终读取
 */
public class PromptRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final NovelProjectPath projectPath;
    private final Map<String, String> selectedDocuments; // path → content

    public PromptRenderer(NovelProjectPath projectPath, Map<String, String> selectedDocuments) {
        this.projectPath = projectPath;
        this.selectedDocuments = selectedDocuments;
    }

    /**
     * 加载 prompt 并替换所有 ${...} 关键词
     */
    public String render(String promptName) {
        String prompt = PromptLoader.load(promptName, projectPath.root());
        return resolve(prompt);
    }

    /**
     * 对任意模板字符串做关键词替换
     */
    public String resolve(String template) {
        String result = template;
        for (var entry : buildContext().entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        result = PLACEHOLDER.matcher(result).replaceAll("(未提供:$1)");
        return result;
    }

    private Map<String, String> buildContext() {
        Map<String, String> ctx = new LinkedHashMap<>();

        // 始终提供的元信息
        ctx.put("${novel_info}", nonNullContent(readRaw(projectPath.outline()), "(无 outline.md)"));
        ctx.put("${story_progress}", nonNullContent(readRaw(projectPath.storyProgress()), "(无 story-progress.md)"));
        ctx.put("${structure}", readBody(projectPath.resolveInsideProject("structure.md")));

        // 按选中文档拼接
        StringBuilder worldSb = null;
        StringBuilder outlineSb = null;
        StringBuilder charactersSb = null;

        for (var entry : selectedDocuments.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            String body = FrontmatterParser.body(content);
            String type = NovelProjectPath.docType(path);

            switch (type) {
                case "world" -> {
                    if (worldSb == null) worldSb = new StringBuilder();
                    worldSb.append("## ").append(path).append("\n\n").append(body).append("\n\n");
                }
                case "outline" -> {
                    if (outlineSb == null) outlineSb = new StringBuilder();
                    outlineSb.append("## ").append(path).append("\n\n").append(body).append("\n\n");
                }
                case "character" -> {
                    if (charactersSb == null) charactersSb = new StringBuilder();
                    charactersSb.append("## ").append(path).append("\n\n").append(body).append("\n\n");
                }
            }
        }

        ctx.put("${world_setting}", worldSb != null ? worldSb.toString() : "(未选中)");
        ctx.put("${outline}", outlineSb != null ? outlineSb.toString() : "(未选中)");
        ctx.put("${characters}", charactersSb != null ? charactersSb.toString() : "(未选中)");

        // 选中的章节大纲和章节
        for (var entry : selectedDocuments.entrySet()) {
            String path = entry.getKey();
            String type = NovelProjectPath.docType(path);
            String body = FrontmatterParser.body(entry.getValue());

            if ("chapter-outline".equals(type)) {
                String filename = Path.of(path).getFileName().toString();
                ctx.put("${outline_" + filename.replace(".md", "").replace("-", "_") + "}", body);
            } else if ("chapter".equals(type)) {
                String filename = Path.of(path).getFileName().toString();
                ctx.put("${chapter_" + filename.replace(".txt", "").replace(".md", "").replace("-", "_") + "}", body);
            }
        }

        return ctx;
    }

    private String readBody(Path path) {
        String raw = readRaw(path);
        if (raw == null) return "(无内容)";
        return FrontmatterParser.body(raw);
    }

    private String readRaw(Path path) {
        if (path == null || !Files.exists(path)) return null;
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "(读取失败)";
        }
    }

    private static String nonNullContent(String raw, String ifMissing) {
        return raw != null ? raw : ifMissing;
    }
}
