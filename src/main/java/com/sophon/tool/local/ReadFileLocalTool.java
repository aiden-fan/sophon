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

/**
 * 按路径读取本地文本文件内容（UTF-8）；有单文件大小上限，默认相对路径相对当前工作目录解析。
 */
public final class ReadFileLocalTool implements LocalTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 默认最大读取字节数（约 512 KiB），防止超大文件撑爆内存。 */
    public static final int DEFAULT_MAX_BYTES = 512 * 1024;

    private final ToolDefinition definition;
    private final int maxBytes;

    public ReadFileLocalTool() {
        this(DEFAULT_MAX_BYTES);
    }

    public ReadFileLocalTool(int maxBytes) {
        this.maxBytes = Math.max(1024, maxBytes);
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
                                  "description": "要读取的文件路径（可为相对路径，相对 JVM 进程当前工作目录）"
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
                        "read_file",
                        "读取本地文件的全部文本内容（UTF-8）。仅支持普通文件，单文件大小有上限。",
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
            throw new ToolExecutionException("缺少 path 字段或为空");
        }
        Path p = Path.of(pathStr.trim()).normalize();
        if (!p.isAbsolute()) {
            p = Path.of(System.getProperty("user.dir", ".")).normalize().resolve(p).normalize();
        }
        if (!Files.exists(p)) {
            return ToolResult.error("文件不存在: " + p);
        }
        if (!Files.isRegularFile(p)) {
            return ToolResult.error("路径不是普通文件: " + p);
        }
        long size;
        try {
            size = Files.size(p);
        } catch (Exception e) {
            throw new ToolExecutionException("无法读取文件大小: " + p, e);
        }
        if (size > maxBytes) {
            return ToolResult.error(
                    "文件过大（"
                            + size
                            + " 字节），超过上限 "
                            + maxBytes
                            + " 字节；请缩小文件或提高工具限制。");
        }
        try {
            byte[] bytes = Files.readAllBytes(p);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return ToolResult.ok(text);
        } catch (Exception e) {
            throw new ToolExecutionException("读取文件失败: " + p + " — " + e.getMessage(), e);
        }
    }
}
