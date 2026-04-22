package com.sophon.core.tool.builtin;

import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 更新根目录 {@code story-progress.md}，记录主线阶段、章节节点、伏笔与待办等。
 */
public class UpdateStoryProgressTool implements Tool {
    private static final String RELATIVE = "story-progress.md";

    private final NovelProjectPath projectPath;

    public UpdateStoryProgressTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "update_story_progress";
    }

    @Override
    public String description() {
        return "在章节完成后，若情节推进、伏笔或阶段目标有变化，写入更新后的完整 story-progress.md（含 YAML frontmatter）。";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "content": {
                  "type": "string",
                  "description": "更新后的完整 story-progress.md 文件内容，必须含 YAML frontmatter（如 description、last_updated_chapter）与正文小节"
                }
              },
              "required": ["content"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String content = (String) args.get("content");
        if (content == null || content.isBlank()) {
            return ToolResult.error("content 不能为空");
        }
        Path fullPath = projectPath.resolveInsideProject(RELATIVE);
        try {
            Files.writeString(fullPath, content);
            return ToolResult.ok("故事线进展已更新: " + RELATIVE);
        } catch (Exception e) {
            return ToolResult.error("写入失败: " + e.getMessage());
        }
    }
}
