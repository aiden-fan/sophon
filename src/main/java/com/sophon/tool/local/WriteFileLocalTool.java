package com.sophon.tool.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.tool.LocalTool;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolExecutionException;
import com.sophon.tool.ToolResult;
import com.sophon.tool.ToolRisk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 将 UTF-8 文本写入本地文件；可选追加、自动创建父目录。与 {@link ReadFileLocalTool} 路径规则一致（相对路径相对 JVM 工作目录）。
 */
public final class WriteFileLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final int MAX_CONTENT_BYTES = 4 * 1024 * 1024;

    private final ToolDefinition definition;

    public WriteFileLocalTool() {
        JsonNode schema;
        try {
            schema =
                    JSON.readTree(
                            """
                            {
                              "type": "object",
                              "properties": {
                                "path": {
                                  "type": "string",
                                  "description": "文件路径（.md 等）；相对路径相对进程工作目录"
                                },
                                "content": {
                                  "type": "string",
                                  "description": "要写入的完整文本（UTF-8）"
                                },
                                "append": {
                                  "type": "boolean",
                                  "description": "为 true 时在文件末尾追加；默认 false 为覆盖写入"
                                },
                                "create_parents": {
                                  "type": "boolean",
                                  "description": "为 true 时自动创建缺失的父目录；默认 false"
                                }
                              },
                              "required": ["path", "content"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "write_file",
                        "写入或覆盖/追加本地文本文件（UTF-8）。用于保存 Markdown 等文档。",
                        schema,
                        ToolRisk.HIGH);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws ToolExecutionException {
        JsonNode args;
        try {
            args = JSON.readTree(argumentsJson);
        } catch (Exception e) {
            throw new ToolExecutionException("参数不是合法 JSON: " + argumentsJson, e);
        }
        String pathStr = args.path("path").asText(null);
        if (pathStr == null || pathStr.isBlank()) {
            throw new ToolExecutionException("缺少 path");
        }
        String content = args.path("content").asText("");
        boolean append = args.path("append").asBoolean(false);
        boolean createParents = args.path("create_parents").asBoolean(false);

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            return ToolResult.error("content 超过上限 " + MAX_CONTENT_BYTES + " 字节");
        }

        Path p = Path.of(pathStr.trim()).normalize();
        if (!p.isAbsolute()) {
            p = Path.of(System.getProperty("user.dir", ".")).normalize().resolve(p).normalize();
        }
        if (createParents) {
            Path parent = p.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (Exception e) {
                    throw new ToolExecutionException("无法创建父目录: " + parent + " — " + e.getMessage(), e);
                }
            }
        }
        try {
            if (append && Files.exists(p)) {
                Files.writeString(p, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } else {
                Files.writeString(p, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
            return ToolResult.ok("已写入: " + p + (append ? "（追加）" : "（覆盖）"));
        } catch (Exception e) {
            throw new ToolExecutionException("写入失败: " + p + " — " + e.getMessage(), e);
        }
    }
}
