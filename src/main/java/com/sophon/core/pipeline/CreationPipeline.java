package com.sophon.core.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.core.context.ContextBuilder;
import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.init.PromptRenderer;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.LlmLogger;
import com.sophon.core.llm.unified.*;
import com.sophon.core.selector.DocumentMeta;
import com.sophon.core.selector.DocumentSelector;
import com.sophon.core.selector.SelectionResult;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolRegistry;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolResult;
import com.sophon.core.tool.builtin.ReadDocumentsTool;
import com.sophon.core.tool.builtin.WriteChapterTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 创作流程编排（统一入口）
 * 所有创作类型（角色/大纲/章节）都走同一流程：
 * 1. 扫描项目文档
 * 2. LLM 决定加载哪些文件
 * 3. 读取选中文件
 * 4. 拼装完整上下文（base prompt + 关键词替换 + 选中文档）
 * 5. 发起 LLM 请求（含 tool call 循环，LLM 可直接调用 write_chapter、update_character 等工具）
 */
public class CreationPipeline {
    private final NovelProjectPath projectPath;
    private final DocumentSelector selector;
    private final ContextBuilder contextBuilder;
    private final LLMProvider llm;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper;

    public CreationPipeline(NovelProjectPath projectPath, DocumentSelector selector,
                            ContextBuilder contextBuilder, LLMProvider llm, ToolRegistry toolRegistry) {
        this.projectPath = projectPath;
        this.selector = selector;
        this.contextBuilder = contextBuilder;
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.mapper = new ObjectMapper();
    }

    /**
     * 创作进度回调
     */
    public interface ProgressListener {
        void onProgress(String message);
        default void onToolCall(String toolName) { onProgress("  🔧 调用工具: " + toolName); }
    }

    /**
     * 通用创作入口
     */
    public String create(String promptName, String userInstruction, OutputHandler outputHandler) {
        return create(promptName, userInstruction, outputHandler, null);
    }

    /**
     * 通用创作入口（含进度回调）
     */
    public String create(String promptName, String userInstruction, OutputHandler outputHandler, ProgressListener listener) {
        LlmLogger logger = new LlmLogger(projectPath.root().resolve("logs"));

        // 1. 扫描项目文档
        emit(listener, "📂 扫描项目文档...");
        List<DocumentMeta> available = scanDocuments();
        emit(listener, "  找到 %d 个文档".formatted(available.size()));

        // 2. LLM 决定加载哪些文件
        emit(listener, "🧠 AI 分析相关文档...");
        SelectionResult selected = selector.select(userInstruction, available);
        Map<String, DocumentMeta> metaByPath = available.stream()
            .collect(Collectors.toMap(DocumentMeta::path, m -> m, (a, b) -> a));
        emit(listener, "  选中 %d 个文档: %s"
            .formatted(selected.paths().size(), selected.paths().stream()
                .map(p -> {
                    DocumentMeta m = metaByPath.get(p);
                    return m != null && m.title() != null ? m.title() : p;
                })
                .collect(java.util.stream.Collectors.joining(", "))));

        // 3. 读取选中文件
        Map<String, String> contents = readDocuments(selected.paths());

        // 4. 拼装完整上下文
        var renderer = new PromptRenderer(projectPath, contents);
        List<UnifiedMessage> messages = contextBuilder.build(contents, userInstruction, renderer, promptName);

        // 5. LLM 生成（含 tool call 循环）
        emit(listener, "🤖 调用 LLM 生成...");
        String content = generateWithToolCall(messages, logger, listener);

        // 6. 写入结果
        outputHandler.write(content);

        return content;
    }

    private void emit(ProgressListener listener, String message) {
        if (listener != null) listener.onProgress(message);
    }

    /**
     * 创作章节（同步）
     * 章节写入和角色状态更新全部由 LLM tool call 处理
     */
    public ChapterResult createChapter(String userInstruction, int chapterNumber, String chapterTitle, ProgressListener listener) {
        LlmLogger logger = new LlmLogger(projectPath.root().resolve("logs"));

        // 1. 扫描文档
        emit(listener, "📂 扫描项目文档...");
        List<DocumentMeta> available = scanDocuments();
        emit(listener, "  找到 %d 个文档".formatted(available.size()));

        // 2. LLM 选文档
        emit(listener, "🧠 AI 分析相关文档...");
        SelectionResult selected = selector.select(userInstruction, available);
        Map<String, DocumentMeta> metaByPath = available.stream()
            .collect(Collectors.toMap(DocumentMeta::path, m -> m, (a, b) -> a));
        emit(listener, "  选中 %d 个文档: %s"
            .formatted(selected.paths().size(), selected.paths().stream()
                .map(p -> {
                    DocumentMeta m = metaByPath.get(p);
                    return m != null && m.title() != null ? m.title() : p;
                })
                .collect(java.util.stream.Collectors.joining(", "))));

        // 3. 读取选中文件 + 组装上下文
        Map<String, String> contents = readDocuments(selected.paths());
        var renderer = new PromptRenderer(projectPath, contents);
        List<UnifiedMessage> messages = contextBuilder.build(contents, userInstruction, renderer, "write-base");

        // 4. 生成章节 — LLM 只需 update_character 工具（章节由 pipeline 直接写入）
        emit(listener, "📝 生成章节内容...");
        List<UnifiedTool> chapterTools = toolRegistry.listByNames(List.of("update_character"));
        String content = generatePhase(messages, chapterTools, "update_character", logger, listener);

        if (content != null) {
            emit(listener, "💾 写入章节文件...");
            WriteChapterTool writer = new WriteChapterTool(projectPath);
            ToolResult result = writer.execute(Map.of(
                "chapter", chapterNumber,
                "title", chapterTitle,
                "content", content
            ));
            emit(listener, "✅ " + result.content());

            // 写入章节后，检查角色状态是否需要更新
            List<UnifiedMessage> updateMessages = buildCharacterUpdateMessages(content);
            if (updateMessages != null) {
                emit(listener, "🔄 检查角色状态是否需要更新...");
                List<UnifiedTool> updateTools = toolRegistry.listByNames(List.of("update_character"));
                LlmLogger charLogger = new LlmLogger(projectPath.root().resolve("logs"));
                generatePhase(updateMessages, updateTools, "update_character", charLogger, listener);
            }
        }

        String filePath = "chapters/chapter-%03d-%s.txt".formatted(chapterNumber, chapterTitle);
        return new ChapterResult(content, projectPath.resolve(filePath).toString());
    }

    /**
     * LLM 调用 + tool call 循环（单阶段生成）
     * 用于角色创建、大纲创建等不需要后续检查的场景
     */
    @SuppressWarnings("unchecked")
    private String generateWithToolCall(List<UnifiedMessage> messages, LlmLogger logger, ProgressListener listener) {
        // 角色/大纲创建只需生成文本，不需要暴露工具
        return generatePhase(messages, List.of(), null, logger, listener);
    }

    /**
     * 单阶段生成：循环直到无 tool call 或无匹配工具
     */
    private String generatePhase(List<UnifiedMessage> messages, List<UnifiedTool> tools, String targetToolName,
                                 LlmLogger logger, ProgressListener listener) {
        List<UnifiedMessage> conversation = new ArrayList<>(messages);
        int maxRounds = 10;
        for (int round = 0; round < maxRounds; round++) {
            UnifiedChatRequest request = UnifiedChatRequest.builder()
                .messages(conversation)
                .tools(tools)
                .build();

            long start = System.currentTimeMillis();
            UnifiedChatResponse response = llm.complete(request);
            long elapsed = System.currentTimeMillis() - start;

            logger.log("Round %d (%s)".formatted(round + 1, targetToolName), request, response, elapsed);

            emit(listener, "  ⏳ LLM 响应中 (%d ms)...".formatted(elapsed));

            if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
                if (response.content() != null && !response.content().isBlank()) {
                    emit(listener, "✅ 生成完成");
                    return response.content();
                }
                return null;
            }

            if (response.content() != null && !response.content().isBlank()) {
                conversation.add(UnifiedMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
            } else {
                conversation.add(UnifiedMessage.assistantWithToolCalls("", response.toolCalls()));
            }

            for (ToolCall tc : response.toolCalls()) {
                emit(listener, "  🔧 调用工具: " + tc.name());
                Tool tool = toolRegistry.get(tc.name());
                if (tool == null) {
                    conversation.add(UnifiedMessage.tool("错误: 未找到工具 " + tc.name(), tc.name(), tc.id()));
                    continue;
                }

                Map<String, Object> args;
                try {
                    args = (tc.argsJson() == null || tc.argsJson().isBlank())
                        ? Map.of()
                        : mapper.readValue(tc.argsJson(), Map.class);
                } catch (Exception e) {
                    conversation.add(UnifiedMessage.tool("参数解析失败: " + e.getMessage(), tc.name(), tc.id()));
                    continue;
                }

                ToolResult toolResult = tool.execute(args);
                emit(listener, "  ✅ " + toolResult.content());
                conversation.add(UnifiedMessage.tool(toolResult.content(), tc.name(), tc.id()));
            }
        }

        return null;
    }

    /**
     * 构建角色更新的干净上下文
     */
    private List<UnifiedMessage> buildCharacterUpdateMessages(String chapterContent) {
        if (chapterContent == null || chapterContent.isBlank()) return null;

        // 构建一个只关注角色的新上下文
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个网络小说的角色状态管理助手。根据刚生成的章节内容，判断是否有角色的状态需要更新。\n\n");
        prompt.append("如果角色在本章中发生了以下变化，请调用 update_character 工具更新角色档案：\n");
        prompt.append("- 修为/能力提升或降级\n");
        prompt.append("- 获得或失去重要物品、能力\n");
        prompt.append("- 人际关系发生变化\n");
        prompt.append("- 角色位置发生变化\n");
        prompt.append("- 角色状态改变（受伤、死亡、复活等）\n\n");
        prompt.append("如果没有角色需要更新，直接回复\"无需更新\"。\n\n");
        prompt.append("=== 刚生成的章节内容 ===\n\n").append(chapterContent).append("\n\n");

        // 读取已选中的角色文档
        Path charsDir = projectPath.charactersDir();
        if (Files.isDirectory(charsDir)) {
            try (java.util.stream.Stream<Path> stream = Files.list(charsDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String body = FrontmatterParser.body(content);
                            prompt.append("=== 角色档案: ").append(p.getFileName()).append(" ===\n\n")
                                .append(body).append("\n\n");
                        } catch (IOException e) {
                            prompt.append("=== 角色档案: ").append(p.getFileName()).append(" ===\n\n")
                                .append("(读取失败: ").append(e.getMessage()).append(")\n\n");
                        }
                    });
            } catch (IOException e) {
                prompt.append("角色目录读取失败: ").append(e.getMessage()).append("\n");
            }
        }

        return List.of(UnifiedMessage.system(prompt.toString()),
            UnifiedMessage.user("请根据以上章节内容，判断是否需要更新角色档案。"));
    }

    /**
     * 创作章节（流式）- 兼容旧接口
     */
    public ChapterResult createStreaming(String userInstruction, int chapterNumber,
                                         String chapterTitle, Consumer<String> onChunk) {
        ChapterResult result = createChapter(userInstruction, chapterNumber, chapterTitle, null);
        if (result.content() != null && onChunk != null) {
            onChunk.accept(result.content());
        }
        return result;
    }

    /**
     * 组装 prompt messages（文档选择 → 读取 → 上下文拼装）
     */
    private List<UnifiedMessage> buildMessages(String promptName, String userInstruction) {
        List<DocumentMeta> available = scanDocuments();
        SelectionResult selected = selector.select(userInstruction, available);
        Map<String, String> contents = readDocuments(selected.paths());
        var renderer = new PromptRenderer(projectPath, contents);
        return contextBuilder.build(contents, userInstruction, renderer, promptName);
    }

    private List<DocumentMeta> scanDocuments() {
        List<String> paths = projectPath.scanAllDocuments();
        return paths.stream()
            .map(p -> {
                String type = NovelProjectPath.docType(p);
                Path fullPath = projectPath.resolve(p);
                String title = extractTitle(fullPath);
                String description = extractDescription(fullPath);
                return new DocumentMeta(p, type, title, description);
            })
            .toList();
    }

    private Map<String, String> readDocuments(List<String> paths) {
        ReadDocumentsTool reader = new ReadDocumentsTool(projectPath);
        return new HashMap<>(reader.readAsMap(paths));
    }

    private String extractTitle(Path file) {
        try {
            String content = Files.readString(file);
            Map<String, Object> meta = FrontmatterParser.parse(content);
            if (meta.containsKey("title")) return String.valueOf(meta.get("title"));
            if (meta.containsKey("name")) return String.valueOf(meta.get("name"));
        } catch (Exception e) {
            return file.getFileName().toString();
        }
        return file.getFileName().toString();
    }

    private String extractDescription(Path file) {
        try {
            String content = Files.readString(file);
            Map<String, Object> meta = FrontmatterParser.parse(content);
            if (meta.containsKey("description")) {
                Object desc = meta.get("description");
                return desc != null ? String.valueOf(desc) : "";
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    /**
     * 结果写入器接口，各创作类型自行定义输出方式
     */
    @FunctionalInterface
    public interface OutputHandler {
        void write(String content);
    }
}
