package com.sophon.core.tool.builtin;

import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 列出项目所有文档及其简要信息
 */
public class ListDocumentsTool implements Tool {
    private final NovelProjectPath projectPath;

    public ListDocumentsTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "list_documents";
    }

    @Override
    public String description() {
        return "列出小说项目中的所有文档文件（大纲、角色、章节等）及其类型";
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
        List<String> paths = projectPath.scanAllDocuments();
        StringBuilder sb = new StringBuilder();
        for (String p : paths) {
            String type = NovelProjectPath.docType(p);
            // Extract title from frontmatter if available
            Path fullPath = projectPath.resolve(p);
            String title = p;
            if (Files.exists(fullPath)) {
                try {
                    String content = Files.readString(fullPath);
                    Map<String, Object> meta = FrontmatterParser.parse(content);
                    if (meta.containsKey("title")) {
                        title = String.valueOf(meta.get("title"));
                    } else if (meta.containsKey("name")) {
                        title = String.valueOf(meta.get("name"));
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            sb.append("- %s [%s] → %s\n".formatted(p, type, title));
        }
        if (sb.isEmpty()) {
            sb.append("(项目为空)\n");
        }
        return ToolResult.ok(sb.toString());
    }
}
