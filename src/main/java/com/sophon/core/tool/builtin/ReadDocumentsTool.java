package com.sophon.core.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 批量读取多个文档内容
 */
public class ReadDocumentsTool implements Tool {
    private final NovelProjectPath projectPath;

    public ReadDocumentsTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "read_documents";
    }

    @Override
    public String description() {
        return "批量读取一个或多个文档的完整内容";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "paths": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "要读取的文档相对路径列表，如 [\"outline.md\", \"characters/张三.md\"]"
                }
              },
              "required": ["paths"]
            }
            """;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> args) {
        List<String> paths = (List<String>) args.getOrDefault("paths", List.of());
        if (paths.isEmpty()) {
            return ToolResult.error("请指定要读取的文档路径");
        }

        StringBuilder sb = new StringBuilder();
        for (String path : paths) {
            Path fullPath = projectPath.resolve(path);
            if (!Files.exists(fullPath)) {
                sb.append("## [%s] (文件不存在)\n\n".formatted(path));
                continue;
            }
            try {
                String content = Files.readString(fullPath);
                sb.append("## [%s]\n\n%s\n\n".formatted(path, content));
            } catch (Exception e) {
                sb.append("## [%s] (读取失败: %s)\n\n".formatted(path, e.getMessage()));
            }
        }
        return ToolResult.ok(sb.toString());
    }
}
