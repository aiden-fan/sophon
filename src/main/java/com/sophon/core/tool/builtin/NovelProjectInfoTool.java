package com.sophon.core.tool.builtin;

import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 获取小说项目概要信息（基于 outline.md / world-setting.md，不再依赖 novel.yaml）
 */
public class NovelProjectInfoTool implements Tool {
    private static final Pattern FIRST_H1 = Pattern.compile("(?m)^#\\s+(.+)$");

    private final NovelProjectPath projectPath;

    public NovelProjectInfoTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "novel_info";
    }

    @Override
    public String description() {
        return "获取小说项目概要（总大纲 outline.md 与进度等）";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {}
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Path outline = projectPath.outline();
        if (!Files.exists(outline)) {
            return ToolResult.error("未找到 outline.md，请确认已用 /novel open 打开小说项目目录");
        }

        try {
            String content = Files.readString(outline);
            Map<String, Object> meta = FrontmatterParser.parse(content);
            String body = FrontmatterParser.body(content);

            String title = firstH1Title(body);
            if (title == null || title.isBlank()) {
                title = "未命名";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📖 %s\n".formatted(title));
            sb.append("大纲说明: %s\n".formatted(meta.getOrDefault("description", "(空)")));
            sb.append("总章数(计划): %s\n".formatted(meta.getOrDefault("total_chapters", "?")));
            sb.append("当前章: %s\n".formatted(meta.getOrDefault("current_chapter", "?")));
            sb.append("路径: %s\n".formatted(projectPath.root()));

            Path world = projectPath.worldSetting();
            if (Files.exists(world)) {
                Map<String, Object> worldMeta = FrontmatterParser.parse(world);
                sb.append("世界观类型: %s\n".formatted(worldMeta.getOrDefault("genre", "(未写)")));
            }

            if (Files.isDirectory(projectPath.chaptersDir())) {
                try (var stream = Files.list(projectPath.chaptersDir())) {
                    long count = stream.filter(Files::isRegularFile).count();
                    sb.append("已写章节: %d\n".formatted(count));
                }
            }

            return ToolResult.ok(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("读取失败: " + e.getMessage());
        }
    }

    private static String firstH1Title(String body) {
        if (body == null) return null;
        Matcher m = FIRST_H1.matcher(body);
        return m.find() ? m.group(1).strip() : null;
    }
}
