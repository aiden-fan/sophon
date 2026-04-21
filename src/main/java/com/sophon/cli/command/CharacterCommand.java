package com.sophon.cli.command;

import com.sophon.core.llm.LLMProvider;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;
import org.jline.terminal.Terminal;

import java.nio.file.Files;

/**
 * 创建角色：用户描述 → LLM 选文档 → 组装上下文 → 生成角色档案 → 写入 characters/
 */
public class CharacterCommand {
    private final Terminal terminal;
    private final CreationPipelineFactory pipelineFactory;

    public CharacterCommand(Terminal terminal, LLMProvider llm, ToolRegistry toolRegistry) {
        this.terminal = terminal;
        this.pipelineFactory = new CreationPipelineFactory(llm, toolRegistry);
    }

    public void execute(String description, NovelProjectPath projectPath) {
        if (projectPath == null) {
            terminal.writer().println("请先打开项目: /novel open <路径> 或创建: /new <描述>");
            terminal.writer().flush();
            return;
        }

        terminal.writer().println("🎭 正在创建角色: " + description);
        terminal.writer().println("  AI 选择文档中...");
        terminal.writer().flush();

        try {
            var pipeline = pipelineFactory.create(projectPath);

            String content = pipeline.create("character-base", description, result -> {
                String charName = InstructionParser.extractCharacterName(result, description);
                String filename = projectPath.sanitizeFileName(charName, "未命名角色") + ".md";
                var target = projectPath.resolveInsideProject("characters/" + filename);
                try {
                    Files.createDirectories(projectPath.charactersDir());
                    Files.writeString(target, result);
                    terminal.writer().println("✅ 角色已写入: " + target);
                    terminal.writer().flush();
                } catch (Exception e) {
                    throw new RuntimeException("写入失败: " + e.getMessage());
                }
            }, msg -> {
                terminal.writer().println(msg);
                terminal.writer().flush();
            });

            terminal.writer().println();
            String preview = content.lines().limit(10).collect(java.util.stream.Collectors.joining("\n"));
            terminal.writer().println("--- 预览 ---");
            terminal.writer().println(preview);
            terminal.writer().println("...");
        } catch (Exception e) {
            terminal.writer().println("❌ 创建角色失败: " + e.getMessage());
            e.printStackTrace(terminal.writer());
        }
        terminal.writer().flush();
    }
}
