package com.sophon.core.tool.builtin;

import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 写入章节文件
 */
public class WriteChapterTool implements Tool {
    private final NovelProjectPath projectPath;

    public WriteChapterTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "write_chapter";
    }

    @Override
    public String description() {
        return "将生成的章节内容写入章节文件";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "chapter": { "type": "integer", "description": "章节编号" },
                "title": { "type": "string", "description": "章节标题" },
                "content": { "type": "string", "description": "章节正文内容" }
              },
              "required": ["chapter", "title", "content"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Object chapterRaw = args.get("chapter");
        String title = String.valueOf(args.getOrDefault("title", "未命名"));
        String content = String.valueOf(args.getOrDefault("content", ""));

        int chapter;
        try {
            chapter = Integer.parseInt(String.valueOf(chapterRaw));
        } catch (Exception e) {
            return ToolResult.error("chapter 参数非法，必须是整数");
        }
        if (content.isBlank()) {
            return ToolResult.error("章节内容不能为空");
        }

        String safeTitle = projectPath.sanitizeFileName(title, "未命名");
        String filename = "chapters/chapter-%03d-%s.txt".formatted(chapter, safeTitle);
        Path fullPath = projectPath.resolveInsideProject(filename);

        try {
            Files.createDirectories(fullPath.getParent());

            String wordCount = countWords(content);
            String frontmatter = """
                ---
                chapter: %d
                title: %s
                word_count: %s
                ---
                """.formatted(chapter, safeTitle, wordCount);

            Files.writeString(fullPath, frontmatter + "\n" + content);
            return ToolResult.ok("章节已写入: " + filename + "（" + wordCount + " 字）");
        } catch (Exception e) {
            return ToolResult.error("写入失败: " + e.getMessage());
        }
    }

    private String countWords(String content) {
        // Count Chinese characters
        int chinese = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') chinese++;
        }
        // Count non-whitespace non-Chinese characters
        int others = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (!((c >= '\u4e00' && c <= '\u9fff') || Character.isWhitespace(c) ||
                  c == '，' || c == '。' || c == '！' || c == '？' || c == '；' || c == '：' ||
                  c == '（' || c == '）' || c == '、')) {
                others++;
            }
        }
        return String.valueOf(chinese + others);
    }
}
