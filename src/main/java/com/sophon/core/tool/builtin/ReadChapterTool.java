package com.sophon.core.tool.builtin;

import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 读取指定章节
 */
public class ReadChapterTool implements Tool {
    private final NovelProjectPath projectPath;

    public ReadChapterTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "read_chapter";
    }

    @Override
    public String description() {
        return "读取指定章节的内容";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "chapter": { "type": "integer", "description": "章节编号" }
              },
              "required": ["chapter"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        int chapter = (int) args.get("chapter");
        Path chaptersDir = projectPath.chaptersDir();

        if (!Files.isDirectory(chaptersDir)) {
            return ToolResult.error("chapters 目录不存在");
        }

        try (var stream = Files.list(chaptersDir)) {
            var matching = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().contains("chapter-%03d".formatted(chapter)))
                .findFirst();

            if (matching.isEmpty()) {
                return ToolResult.error("未找到第 " + chapter + " 章");
            }

            Path file = matching.get();
            String content = Files.readString(file);
            Map<String, Object> meta = FrontmatterParser.parse(content);
            String body = FrontmatterParser.body(content);

            StringBuilder sb = new StringBuilder();
            if (meta.containsKey("title")) {
                sb.append("## 第 %d 章 %s\n\n".formatted(chapter, meta.get("title")));
            }
            if (meta.containsKey("word_count")) {
                sb.append("（%s 字）\n\n".formatted(meta.get("word_count")));
            }
            sb.append(body);
            return ToolResult.ok(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("读取失败: " + e.getMessage());
        }
    }
}
