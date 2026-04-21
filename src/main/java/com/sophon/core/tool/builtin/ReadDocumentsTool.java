package com.sophon.core.tool.builtin;

import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

        Map<String, String> docs = readAsMap(paths);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : docs.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            if (content == null) {
                sb.append("## [%s] (文件不存在)\n\n".formatted(path));
                continue;
            }
            sb.append("## [%s]\n\n%s\n\n".formatted(path, content));
        }
        return ToolResult.ok(sb.toString());
    }

    public Map<String, String> readAsMap(List<String> paths) {
        Map<String, String> docs = new LinkedHashMap<>();
        for (String path : paths) {
            try {
                Path fullPath = projectPath.resolveInsideProject(path);
                if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
                    docs.put(path, null);
                    continue;
                }
                docs.put(path, Files.readString(fullPath));
            } catch (Exception e) {
                docs.put(path, "(读取失败: " + e.getMessage() + ")");
            }
        }
        return docs;
    }
}
