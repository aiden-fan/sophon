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
 * LLM 请求/响应日志记录器。
 * 构造时确定一个日志文件；同一次用户请求内应复用同一实例，使文档筛选、正文生成、
 * 角色/故事线检查等多轮 LLM 调用追加写入同一文件。
 * 流程结束时调用 {@link #finishSession()}，将把本实例内累加的 token 汇总写入该文件最开头。
 */
public class LlmLogger {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path logFile;
    private final ObjectMapper mapper;

    /** 各次 {@link #log} 中 response.usage() 的累加（无 usage 的轮次不计入） */
    private long accumulatedPromptTokens;
    private long accumulatedCompletionTokens;
    /** 含非 null usage 的调用次数 */
    private int usageCallCount;
    private boolean sessionFinished;

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
        if (response != null && response.usage() != null) {
            accumulatedPromptTokens += response.usage().promptTokens();
            accumulatedCompletionTokens += response.usage().completionTokens();
            usageCallCount++;
        }
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

    /**
     * 流程结束时调用：把累计的 token 消耗写入日志文件最开头（幂等，成功执行后仅生效一次）。
     */
    public void finishSession() {
        synchronized (this) {
            if (sessionFinished) {
                return;
            }
            String header = buildTokenSummaryHeader();
            try {
                if (!Files.exists(logFile)) {
                    Files.writeString(logFile, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
                } else {
                    String body = Files.readString(logFile, StandardCharsets.UTF_8);
                    Files.writeString(logFile, header + body, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
                sessionFinished = true;
            } catch (IOException ignored) {
            }
        }
    }

    private String buildTokenSummaryHeader() {
        long total = accumulatedPromptTokens + accumulatedCompletionTokens;
        return """
            ================================================================================
            本次请求 Token 汇总（以下为流程内已记录 LLM 调用的 prompt + completion 累加，供粗算成本）
            prompt_tokens（累加）: %d
            completion_tokens（累加）: %d
            合计: %d
            含 usage 的调用次数: %d
            日志文件: %s
            ================================================================================

            """.formatted(
            accumulatedPromptTokens,
            accumulatedCompletionTokens,
            total,
            usageCallCount,
            logFile.getFileName());
    }

    public Path logFile() {
        return logFile;
    }
}
