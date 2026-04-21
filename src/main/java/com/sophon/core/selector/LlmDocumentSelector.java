package com.sophon.core.selector;

import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.LlmLogger;
import com.sophon.core.llm.unified.*;
import com.sophon.core.tool.NovelProjectPath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的文档选择（使用 function call 获取结构化结果）
 * 把项目文件清单 + 大纲/主角信息发给 LLM，让 LLM 根据用户指令决定需要加载哪些文档
 */
public class LlmDocumentSelector implements DocumentSelector {
    private final LLMProvider llm;
    private final NovelProjectPath projectPath;
    private final LlmLogger logger;

    public LlmDocumentSelector(LLMProvider llm, NovelProjectPath projectPath) {
        this.llm = llm;
        this.projectPath = projectPath;
        this.logger = (projectPath != null)
            ? new LlmLogger(projectPath.root().resolve("logs"))
            : null;
    }

    @Override
    public SelectionResult select(String userInstruction, List<DocumentMeta> available) {
        // 构建系统 prompt：文档清单 + 大纲/主角参考信息
        String systemPrompt = buildSystemPrompt(available);

        // 定义工具：让 LLM 用结构化参数返回选择的文档
        String toolSchema = """
            {
              "type": "object",
              "properties": {
                "selected_paths": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "需要参考的文档路径列表，必须是文档清单中的路径"
                }
              },
              "required": ["selected_paths"]
            }
            """;

        var messages = List.of(
            UnifiedMessage.system(systemPrompt),
            UnifiedMessage.user(userInstruction)
        );

        var request = UnifiedChatRequest.builder()
            .messages(messages)
            .tools(List.of(new UnifiedTool("select_documents", "选择需要参考的文档", toolSchema)))
            .temperature(0.1)
            .maxTokens(256)
            .build();

        try {
            long start = System.currentTimeMillis();
            UnifiedChatResponse response = llm.complete(request);
            long elapsed = System.currentTimeMillis() - start;

            // 记录筛选请求和响应
            if (logger != null) logger.log("DocumentSelect", request, response, elapsed);

            // 从 tool_calls 中提取结果
            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                for (ToolCall tc : response.toolCalls()) {
                    if ("select_documents".equals(tc.name())) {
                        return parseToolArgs(tc.argsJson(), available);
                    }
                }
            }

            // 如果 LLM 没有返回 tool_call，降级
            return fallbackAll(available);
        } catch (Exception e) {
            if (logger != null) logger.logError("DocumentSelect", request, e);
            return fallbackAll(available);
        }
    }

    /**
     * 构建文档选择专用的系统 prompt
     */
    private String buildSystemPrompt(List<DocumentMeta> available) {
        String outline = readDocument("outline.md");
        String protagonist = readProtagonist();
        String docList = formatDocList(available);

        return """
            你是一个文档筛选助手。根据用户指令，从可用文档列表中选择最相关的文档。

            %s
            %s
            === 可用文档列表 ===

            %s

            请调用 select_documents 工具，返回需要参考的文档路径列表。
            """.formatted(
            outline != null ? "=== 小说大纲 ===\n" + outline + "\n" : "",
            protagonist != null ? "=== 主角信息 ===\n" + protagonist + "\n" : "",
            docList
        );
    }

    private String formatDocList(List<DocumentMeta> available) {
        return available.stream()
            .map(m -> "- %s: %s%s".formatted(
                m.path(),
                m.type(),
                m.description() != null && !m.description().isBlank()
                    ? " — " + m.description()
                    : ""
            ))
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 读取指定文档内容（去 frontmatter 后的正文）
     */
    private String readDocument(String relativePath) {
        if (projectPath == null) return null;
        var fullPath = projectPath.resolve(relativePath);
        try {
            if (Files.exists(fullPath)) {
                String content = Files.readString(fullPath, StandardCharsets.UTF_8);
                return FrontmatterParser.body(content);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * 读取主角角色文档
     */
    private String readProtagonist() {
        if (projectPath == null) return null;
        var charsDir = projectPath.charactersDir();
        if (!Files.isDirectory(charsDir)) return null;

        try (var stream = Files.list(charsDir)) {
            return stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .filter(p -> isProtagonist(p))
                .findFirst()
                .map(p -> {
                    try {
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        return FrontmatterParser.body(content);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .orElse(null);
        } catch (IOException ignored) {
        }
        return null;
    }

    private boolean isProtagonist(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> meta = FrontmatterParser.parse(content);
            Object role = meta.get("role");
            return "protagonist".equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private SelectionResult parseToolArgs(String argsJson, List<DocumentMeta> available) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            List<String> selected = (List<String>) args.get("selected_paths");

            // 过滤掉无效路径
            List<String> valid = selected.stream()
                .filter(p -> available.stream().anyMatch(m -> m.path().equals(p)))
                .toList();

            if (valid.isEmpty()) return fallbackAll(available);
            return new SelectionResult(valid);
        } catch (Exception e) {
            return fallbackAll(available);
        }
    }

    private SelectionResult fallbackAll(List<DocumentMeta> available) {
        return new SelectionResult(available.stream()
            .filter(m -> !"chapter".equals(m.type()))
            .map(DocumentMeta::path)
            .toList());
    }
}
