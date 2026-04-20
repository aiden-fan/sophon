package com.sophon.cli.command;

import com.sophon.core.context.DefaultContextBuilder;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.pipeline.ChapterResult;
import com.sophon.core.pipeline.CreationPipeline;
import com.sophon.core.selector.LlmDocumentSelector;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;
import org.jline.terminal.Terminal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WriteCommand {
    private final Terminal terminal;
    private final LLMProvider llm;
    private final ToolRegistry toolRegistry;

    public WriteCommand(Terminal terminal, LLMProvider llm, ToolRegistry toolRegistry) {
        this.terminal = terminal;
        this.llm = llm;
        this.toolRegistry = toolRegistry;
    }

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第?(\\d+)章");

    public void execute(String userInstruction, NovelProjectPath projectPath) {
        if (projectPath == null) {
            terminal.writer().println("请先打开项目: /novel open <路径> 或创建: /new <描述>");
            terminal.writer().flush();
            return;
        }

        // Extract chapter number from instruction
        int chapterNumber = extractChapter(userInstruction);
        String chapterTitle = extractTitle(userInstruction);

        // Build pipeline
        var selector = new LlmDocumentSelector(llm);
        var contextBuilder = new DefaultContextBuilder();
        var pipeline = new CreationPipeline(projectPath, selector, contextBuilder, llm, toolRegistry);

        terminal.writer().println("📝 开始创作...");
        terminal.writer().println("  章节: 第%d章 %s".formatted(chapterNumber, chapterTitle));
        terminal.writer().println("  AI 选择文档中...");
        terminal.writer().flush();

        try {
            ChapterResult result = pipeline.createChapter(userInstruction, chapterNumber, chapterTitle);
            terminal.writer().println("✅ 章节已写入: " + result.filePath());
            terminal.writer().println();
            // Preview first few lines
            String preview = result.content().lines().limit(5).collect(java.util.stream.Collectors.joining("\n"));
            terminal.writer().println("--- 预览 ---");
            terminal.writer().println(preview);
            terminal.writer().println("...");
            terminal.writer().println("------------");
        } catch (Exception e) {
            terminal.writer().println("❌ 创作失败: " + e.getMessage());
            e.printStackTrace(terminal.writer());
        }
        terminal.writer().flush();
    }

    private int extractChapter(String instruction) {
        Matcher m = CHAPTER_PATTERN.matcher(instruction);
        if (m.find()) return Integer.parseInt(m.group(1));
        // Try to find just a number
        m = Pattern.compile("(\\d+)").matcher(instruction);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 1; // default to chapter 1
    }

    private String extractTitle(String instruction) {
        // Try to extract a meaningful title from the instruction
        String cleaned = instruction.replaceAll("写第\\d+章\\s*", "")
            .replaceAll("第\\d+章\\s*", "")
            .replaceAll("写", "")
            .replaceAll("续写", "")
            .replaceAll("创作", "")
            .trim();
        // Keep it short - max 10 chars
        if (cleaned.isBlank()) return "未命名";
        return cleaned.length() > 15 ? cleaned.substring(0, 15) : cleaned;
    }
}
