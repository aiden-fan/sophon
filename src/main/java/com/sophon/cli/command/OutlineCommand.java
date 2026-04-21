package com.sophon.cli.command;

import com.sophon.core.context.DefaultContextBuilder;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.pipeline.CreationPipeline;
import com.sophon.core.selector.LlmDocumentSelector;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;
import org.jline.terminal.Terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 创建章节大纲：AI 读取设定后生成指定章节大纲 → 写入 outlines/
 */
public class OutlineCommand {
    private final Terminal terminal;
    private final LLMProvider llm;
    private final ToolRegistry toolRegistry;

    public OutlineCommand(Terminal terminal, LLMProvider llm, ToolRegistry toolRegistry) {
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

        int chapterNumber = extractChapter(userInstruction);

        terminal.writer().println("📋 正在创建章节大纲: 第" + chapterNumber + "章");
        terminal.writer().flush();

        try {
            var selector = new LlmDocumentSelector(llm);
            var contextBuilder = new DefaultContextBuilder();
            var pipeline = new CreationPipeline(projectPath, selector, contextBuilder, llm, toolRegistry);

            String content = pipeline.create("outline-base", userInstruction, result -> {
                Path outlinesDir = projectPath.outlinesDir();
                try {
                    Files.createDirectories(outlinesDir);
                    String filename = "chapter-%03d.md".formatted(chapterNumber);
                    Path target = outlinesDir.resolve(filename);
                    Files.writeString(target, result);
                } catch (Exception e) {
                    throw new RuntimeException("写入失败: " + e.getMessage());
                }
            }, msg -> terminal.writer().println(msg));

            terminal.writer().println();
            String preview = content.lines().limit(10).collect(Collectors.joining("\n"));
            terminal.writer().println("--- 预览 ---");
            terminal.writer().println(preview);
            terminal.writer().println("...");
        } catch (Exception e) {
            terminal.writer().println("❌ 创建大纲失败: " + e.getMessage());
            e.printStackTrace(terminal.writer());
        }
        terminal.writer().flush();
    }

    private int extractChapter(String instruction) {
        Matcher m = CHAPTER_PATTERN.matcher(instruction);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = Pattern.compile("(\\d+)").matcher(instruction);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 1;
    }
}
