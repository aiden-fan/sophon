package com.sophon.cli.command;

import com.sophon.core.llm.LLMProvider;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;
import org.jline.terminal.Terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * 创建章节大纲：AI 读取设定后生成指定章节大纲 → 写入 outlines/
 */
public class OutlineCommand {
    private final Terminal terminal;
    private final CreationPipelineFactory pipelineFactory;

    public OutlineCommand(Terminal terminal, LLMProvider llm, ToolRegistry toolRegistry) {
        this.terminal = terminal;
        this.pipelineFactory = new CreationPipelineFactory(llm, toolRegistry);
    }

    public void execute(String userInstruction, NovelProjectPath projectPath) {
        if (projectPath == null) {
            terminal.writer().println("请先打开项目: /novel open <路径> 或创建: /new <描述>");
            terminal.writer().flush();
            return;
        }

        int chapterNumber = InstructionParser.extractChapter(userInstruction);

        terminal.writer().println("📋 正在创建章节大纲: 第" + chapterNumber + "章");
        terminal.writer().flush();

        try {
            var pipeline = pipelineFactory.create(projectPath);

            String content = pipeline.create("outline-base", userInstruction, result -> {
                Path outlinesDir = projectPath.outlinesDir();
                try {
                    Files.createDirectories(outlinesDir);
                    String filename = "chapter-%03d.md".formatted(chapterNumber);
                    Path target = outlinesDir.resolve(filename);
                    Files.writeString(target, result);
                    terminal.writer().flush();
                } catch (Exception e) {
                    throw new RuntimeException("写入失败: " + e.getMessage());
                }
            }, msg -> {
                terminal.writer().println(msg);
                terminal.writer().flush();
            });

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
}
