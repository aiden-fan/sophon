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
 * 新建本地文件（UTF-8）：路径不存在时创建；**若文件已存在则失败**（避免误覆盖，覆盖请用 {@link WriteFileLocalTool}）。
 * 路径规则与 {@link ReadFileLocalTool} 一致。
 */
public final class CreateFileLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final int MAX_CONTENT_BYTES = WriteFileLocalTool.MAX_CONTENT_BYTES;

    private final ToolDefinition definition;

    public CreateFileLocalTool() {
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
                                  "description": "新文件路径；相对路径相对进程工作目录；已存在时失败"
                                },
                                "content": {
                                  "type": "string",
                                  "description": "初始内容（UTF-8），默认空文件"
                                },
                                "create_parents": {
                                  "type": "boolean",
                                  "description": "为 true 时自动创建缺失的父目录；默认 false"
                                }
                              },
                              "required": ["path"]
                            }
                            """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.definition =
                new ToolDefinition(
                        "create_file",
                        "新建本地文本文件（仅当路径尚不存在）。已有文件请用 write_file。",
                        schema,
                        ToolRisk.STANDARD);
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
        boolean createParents = args.path("create_parents").asBoolean(false);

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            return ToolResult.error("content 超过上限 " + MAX_CONTENT_BYTES + " 字节");
        }

        Path p = Path.of(pathStr.trim()).normalize();
        if (!p.isAbsolute()) {
            p = Path.of(System.getProperty("user.dir", ".")).normalize().resolve(p).normalize();
        }
        if (Files.exists(p)) {
            return ToolResult.error(
                    "文件已存在，无法新建: "
                            + p
                            + "（若需覆盖或追加请使用 write_file）");
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
            Files.write(
                    p,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return ToolResult.ok("已新建: " + p + (bytes.length == 0 ? "（空文件）" : ""));
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return ToolResult.error("文件已存在: " + p);
        } catch (Exception e) {
            throw new ToolExecutionException("新建失败: " + p + " — " + e.getMessage(), e);
        }
    }
}
