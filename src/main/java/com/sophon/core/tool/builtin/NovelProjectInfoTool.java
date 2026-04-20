package com.sophon.core.tool.builtin;

import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 获取小说项目元信息
 */
public class NovelProjectInfoTool implements Tool {
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
        return "获取小说项目的元信息（标题、类型、当前进度等）";
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
        Path novelYaml = projectPath.novelYaml();
        if (!Files.exists(novelYaml)) {
            return ToolResult.error("未找到 novel.yaml，请先用 /novel open 打开项目");
        }

        try {
            String content = Files.readString(novelYaml);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = FrontmatterParser.parse(content);

            StringBuilder sb = new StringBuilder();
            sb.append("📖 %s\n".formatted(meta.getOrDefault("title", "未命名")));
            sb.append("类型: %s\n".formatted(meta.getOrDefault("genre", "未指定")));
            sb.append("简介: %s\n".formatted(meta.getOrDefault("description", "(空)")));
            sb.append("路径: %s\n".formatted(projectPath.root()));

            // Count chapters
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
}
