package com.sophon.core.tool.builtin;

import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolResult;

import java.util.Map;

public class EchoTool implements Tool {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "回显输入的参数（调试用）";
    }

    @Override
    public String parametersSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "text": { "type": "string", "description": "要回显的文字" }
              },
              "required": ["text"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String text = (String) args.getOrDefault("text", "");
        return ToolResult.ok("[echo] " + text);
    }
}
