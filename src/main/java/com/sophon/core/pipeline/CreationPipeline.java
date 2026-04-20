package com.sophon.core.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.core.context.ContextBuilder;
import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.init.PromptRenderer;
import com.sophon.core.llm.LLMProvider;
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
        // 1. 扫描项目文档
        List<DocumentMeta> available = scanDocuments();

        // 2. LLM 决定加载哪些文件
        SelectionResult selected = selector.select(userInstruction, available);

        // 3. 读取选中文件
        Map<String, String> contents = readDocuments(selected.paths());

        // 4. 拼装完整上下文
        var renderer = new PromptRenderer(projectPath);
        List<UnifiedMessage> messages = contextBuilder.build(contents, userInstruction, renderer, promptName);

        // 5. LLM 生成（含 tool call 循环）
        String content = generateWithToolCall(messages);

        // 6. 写入结果
        outputHandler.write(content);

        return content;
    }

    /**
     * 创作章节（同步）
     * 章节写入和角色状态更新全部由 LLM tool call 处理
     */
    public ChapterResult createChapter(String userInstruction, int chapterNumber, String chapterTitle) {
        String content = generateWithToolCall(
            buildMessages("write-base", userInstruction)
        );

        String filePath = "chapters/chapter-%03d-%s.txt".formatted(chapterNumber, chapterTitle);
        return new ChapterResult(content, projectPath.resolve(filePath).toString());
    }

    /**
     * LLM 调用 + tool call 循环
     */
    @SuppressWarnings("unchecked")
    private String generateWithToolCall(List<UnifiedMessage> messages) {
        List<UnifiedMessage> conversation = new ArrayList<>(messages);
        List<UnifiedTool> tools = toolRegistry.listAll();

        int maxRounds = 10;
        for (int round = 0; round < maxRounds; round++) {
            UnifiedChatResponse response = llm.complete(UnifiedChatRequest.builder()
                .messages(conversation)
                .tools(tools)
                .build());

            if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
                conversation.add(UnifiedMessage.assistant(response.content()));
                return response.content();
            }

            for (ToolCall tc : response.toolCalls()) {
                Tool tool = toolRegistry.get(tc.name());
                if (tool == null) {
                    conversation.add(UnifiedMessage.assistant("工具调用: " + tc.name()));
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
        }

        return "(达到最大工具调用轮次，请检查工具逻辑)";
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
        var renderer = new PromptRenderer(projectPath);
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
