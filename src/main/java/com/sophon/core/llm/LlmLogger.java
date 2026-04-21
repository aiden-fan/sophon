package com.sophon.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sophon.core.llm.unified.UnifiedChatRequest;
import com.sophon.core.llm.unified.UnifiedChatResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LLM 请求/响应日志记录器
 * 每次用户操作创建一个日志文件，记录该操作中的所有 LLM 调用
 */
public class LlmLogger {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path logFile;
    private final ObjectMapper mapper;

    public LlmLogger(Path logDir) {
        try {
            Files.createDirectories(logDir);
            this.logFile = logDir.resolve("llm-" + LocalDateTime.now().format(TS) + ".log");
        } catch (IOException e) {
            throw new RuntimeException("创建日志目录失败", e);
        }
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 记录一次 LLM 调用
     */
    public void log(String operation, UnifiedChatRequest request, UnifiedChatResponse response, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== %s ====\n".formatted(operation));
        sb.append("时间: %s\n".formatted(LocalDateTime.now().format(TIME)));
        sb.append("耗时: %d ms\n\n".formatted(elapsedMs));
        sb.append("--- Request ---\n");
        try {
            sb.append(mapper.writeValueAsString(request)).append("\n\n");
        } catch (Exception e) {
            sb.append("(序列化失败: ").append(e.getMessage()).append(")\n\n");
        }
        sb.append("--- Response ---\n");
        try {
            sb.append(mapper.writeValueAsString(response)).append("\n\n");
        } catch (Exception e) {
            sb.append("(序列化失败: ").append(e.getMessage()).append(")\n\n");
        }
        sb.append("=".repeat(80)).append("\n\n");

        try {
            Files.writeString(logFile, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /**
     * 记录 LLM 调用异常
     */
    public void logError(String operation, UnifiedChatRequest request, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== %s (ERROR) ====\n".formatted(operation));
        sb.append("时间: %s\n".formatted(LocalDateTime.now().format(TIME)));
        sb.append("--- Request ---\n");
        try {
            sb.append(mapper.writeValueAsString(request)).append("\n\n");
        } catch (Exception ex) {
            sb.append("(序列化失败)\n\n");
        }
        sb.append("--- Error ---\n");
        sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n\n");
        sb.append("=".repeat(80)).append("\n\n");

        try {
            Files.writeString(logFile, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public Path logFile() {
        return logFile;
    }
}
