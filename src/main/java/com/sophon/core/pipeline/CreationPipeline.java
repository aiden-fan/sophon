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
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
     * 通用创作入口
     * @param promptName  base prompt 名称（如 "character-base", "outline-base", "write-base"）
     * @param userInstruction 用户指令
     * @param outputHandler 结果写入器
     */
    public String create(String promptName, String userInstruction, OutputHandler outputHandler) {
        LlmLogger logger = new LlmLogger(projectPath.root().resolve("logs"));

        // 1. 扫描项目文档
        List<DocumentMeta> available = scanDocuments();

        // 2. LLM 决定加载哪些文件
        SelectionResult selected = selector.select(userInstruction, available);

        // 3. 读取选中文件
        Map<String, String> contents = readDocuments(selected.paths());

        // 4. 拼装完整上下文（基于已选文档按需拼接）
        var renderer = new PromptRenderer(projectPath, contents);
        List<UnifiedMessage> messages = contextBuilder.build(contents, userInstruction, renderer, promptName);

        // 5. LLM 生成（含 tool call 循环）
        String content = generateWithToolCall(messages, logger);

        // 6. 写入结果
        outputHandler.write(content);

        return content;
    }

    /**
     * 创作章节（同步）
     * 章节写入和角色状态更新全部由 LLM tool call 处理
     */
    public ChapterResult createChapter(String userInstruction, int chapterNumber, String chapterTitle) {
        LlmLogger logger = new LlmLogger(projectPath.root().resolve("logs"));

        String content = generateWithToolCall(
            buildMessages("write-base", userInstruction),
            logger
        );

        String filePath = "chapters/chapter-%03d-%s.txt".formatted(chapterNumber, chapterTitle);
        return new ChapterResult(content, projectPath.resolve(filePath).toString());
    }

    /**
     * LLM 调用 + tool call 循环
     * 分两个阶段：
     * Phase 1: 生成章节内容，写章节
     * Phase 2: 用新上下文（base_prompt + 已选角色文档 + 新章节内容）判断是否需要更新角色
     */
    @SuppressWarnings("unchecked")
    private String generateWithToolCall(List<UnifiedMessage> messages, LlmLogger logger) {
        List<UnifiedTool> tools = toolRegistry.listAll();

        // Phase 1: 生成章节
        String chapterContent = generatePhase(messages, tools, "write_chapter", logger);

        // Phase 2: 判断是否需要更新角色（用新干净上下文）
        List<UnifiedMessage> updateMessages = buildCharacterUpdateMessages(chapterContent);
        if (updateMessages != null) {
            List<UnifiedTool> updateTools = toolRegistry.listAll().stream()
                .filter(t -> "update_character".equals(t.name()))
                .toList();
            generatePhase(updateMessages, updateTools, "update_character", logger);
        }

        return chapterContent;
    }

    /**
     * 单阶段生成：循环直到无 tool call 或无匹配工具
     */
    private String generatePhase(List<UnifiedMessage> messages, List<UnifiedTool> tools, String targetToolName, LlmLogger logger) {
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

            if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
                if (response.content() != null && !response.content().isBlank()) {
                    return response.content();
                }
                return null;
            }

            ToolCall tc = response.toolCalls().get(0);
            Tool tool = toolRegistry.get(tc.name());
            if (tool == null) {
                conversation.add(UnifiedMessage.user("错误: 未找到工具 " + tc.name()));
                continue;
            }

            Map<String, Object> args;
            try {
                args = mapper.readValue(tc.argsJson(), Map.class);
            } catch (Exception e) {
                conversation.add(UnifiedMessage.user("工具 " + tc.name() + " 参数解析失败: " + e.getMessage()));
                continue;
            }

            ToolResult toolResult = tool.execute(args);
            conversation.add(UnifiedMessage.user("[" + tc.name() + "] 执行结果:\n" + toolResult.content()));
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
                        } catch (IOException ignored) {
                        }
                    });
            } catch (IOException ignored) {
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
        List<UnifiedMessage> messages = buildMessages("write-base", userInstruction);

        StringBuilder fullContent = new StringBuilder();
        Publisher<UnifiedStreamEvent> stream = llm.stream(UnifiedChatRequest.builder()
            .messages(messages)
            .build());

        AtomicReference<Throwable> error = new AtomicReference<>();
        stream.subscribe(new org.reactivestreams.Subscriber<UnifiedStreamEvent>() {
            org.reactivestreams.Subscription sub;
            @Override public void onSubscribe(org.reactivestreams.Subscription s) { this.sub = s; s.request(Long.MAX_VALUE); }
            @Override public void onNext(UnifiedStreamEvent event) {
                if (!event.isDone()) {
                    fullContent.append(event.delta());
                    onChunk.accept(event.delta());
                }
            }
            @Override public void onError(Throwable t) { error.set(t); }
            @Override public void onComplete() { }
        });

        if (error.get() != null) {
            throw new RuntimeException("流式生成失败: " + error.get().getMessage(), error.get());
        }

        WriteChapterTool writer = new WriteChapterTool(projectPath);
        ToolResult result = writer.execute(Map.of(
            "chapter", chapterNumber,
            "title", chapterTitle,
            "content", fullContent.toString()
        ));

        if (result.isError()) {
            throw new RuntimeException("写入章节失败: " + result.content());
        }

        return new ChapterResult(fullContent.toString(),
            projectPath.resolve("chapters/chapter-%03d-%s.md".formatted(chapterNumber, chapterTitle)).toString());
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
                String title = extractTitle(projectPath.resolve(p));
                return new DocumentMeta(p, type, title);
            })
            .toList();
    }

    private Map<String, String> readDocuments(List<String> paths) {
        ReadDocumentsTool reader = new ReadDocumentsTool(projectPath);
        ToolResult result = reader.execute(Map.of("paths", paths));
        if (result.isError()) {
            throw new RuntimeException("读取文档失败: " + result.content());
        }

        Map<String, String> contents = new HashMap<>();
        for (String path : paths) {
            Path fullPath = projectPath.resolve(path);
            if (Files.exists(fullPath)) {
                try {
                    contents.put(path, Files.readString(fullPath));
                } catch (IOException e) {
                    contents.put(path, "(读取失败: " + e.getMessage() + ")");
                }
            }
        }
        return contents;
    }

    private String extractTitle(Path file) {
        try {
            String content = Files.readString(file);
            Map<String, Object> meta = FrontmatterParser.parse(content);
            if (meta.containsKey("title")) return String.valueOf(meta.get("title"));
            if (meta.containsKey("name")) return String.valueOf(meta.get("name"));
        } catch (Exception ignored) {}
        return file.getFileName().toString();
    }

    /**
     * 结果写入器接口，各创作类型自行定义输出方式
     */
    @FunctionalInterface
    public interface OutputHandler {
        void write(String content);
    }
}
