package com.sophon.core.tool.builtin;

import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 更新角色档案（属性、位置、人际关系等状态变化时调用）
 */
public class UpdateCharacterTool implements Tool {
    private final NovelProjectPath projectPath;

    public UpdateCharacterTool(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String name() {
        return "update_character";
    }

    @Override
    public String description() {
        return "当角色的属性、位置、人际关系等状态发生变化时，更新对应角色档案文件。"
             + "传入角色名称和完整的更新后角色档案内容（含 YAML frontmatter）。";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "name": { "type": "string", "description": "角色名称，用于确定文件名" },
                "content": { "type": "string", "description": "更新后的完整角色档案内容，必须包含 YAML frontmatter（name, role, status）和角色描述（外貌、性格、修为、背景、人际关系等）" }
              },
              "required": ["name", "content"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String name = (String) args.get("name");
        String content = (String) args.get("content");

        if (name == null || name.isBlank()) {
            return ToolResult.error("角色名称不能为空");
        }
        if (content == null || content.isBlank()) {
            return ToolResult.error("角色内容不能为空");
        }

        String safeName = projectPath.sanitizeFileName(name, "未命名角色");
        String filename = "characters/" + safeName + ".md";
        Path fullPath = projectPath.resolveInsideProject(filename);

        try {
            Files.createDirectories(fullPath.getParent());
            Files.writeString(fullPath, content);
            return ToolResult.ok("角色档案已更新: " + filename);
        } catch (Exception e) {
            return ToolResult.error("更新角色档案失败: " + e.getMessage());
        }
    }
}
