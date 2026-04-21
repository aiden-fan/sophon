package com.sophon.cli.command;

import com.sophon.core.llm.LLMProvider;
import com.sophon.core.pipeline.ChapterResult;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;
import org.jline.terminal.Terminal;

import java.util.stream.Collectors;

public class WriteCommand {
    private final Terminal terminal;
    private final CreationPipelineFactory pipelineFactory;

    public WriteCommand(Terminal terminal, LLMProvider llm, ToolRegistry toolRegistry) {
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
        String chapterTitle = InstructionParser.extractChapterTitle(userInstruction, projectPath);
        var pipeline = pipelineFactory.create(projectPath);

        terminal.writer().println("📝 开始创作: 第%d章 %s".formatted(chapterNumber, chapterTitle));
        terminal.writer().flush();

        try {
            ChapterResult result = pipeline.createChapter(userInstruction, chapterNumber, chapterTitle,
                msg -> {
                    terminal.writer().println(msg);
                    terminal.writer().flush();
                });
            terminal.writer().println();
            String preview = result.content().lines().limit(5).collect(Collectors.joining("\n"));
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
}
