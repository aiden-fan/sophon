package com.sophon.cli.command;

import com.sophon.core.context.DefaultContextBuilder;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.pipeline.CreationPipeline;
import com.sophon.core.selector.LlmDocumentSelector;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;
import org.jline.terminal.Terminal;

import java.nio.file.Files;

/**
 * 创建角色：用户描述 → LLM 选文档 → 组装上下文 → 生成角色档案 → 写入 characters/
 */
public class CharacterCommand {
    private final Terminal terminal;
    private final LLMProvider llm;
    private final ToolRegistry toolRegistry;

    public CharacterCommand(Terminal terminal, LLMProvider llm, ToolRegistry toolRegistry) {
        this.terminal = terminal;
        this.llm = llm;
        this.toolRegistry = toolRegistry;
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
            var selector = new LlmDocumentSelector(llm);
            var contextBuilder = new DefaultContextBuilder();
            var pipeline = new CreationPipeline(projectPath, selector, contextBuilder, llm, toolRegistry);

            String content = pipeline.create("character-base", description, result -> {
                String charName = extractCharacterName(result, description);
                String filename = sanitize(charName) + ".md";
                var target = projectPath.charactersDir().resolve(filename);
                try {
                    Files.createDirectories(projectPath.charactersDir());
                    Files.writeString(target, result);
                    terminal.writer().println("✅ 角色已写入: " + target);
                } catch (Exception e) {
                    throw new RuntimeException("写入失败: " + e.getMessage());
                }
            }, msg -> terminal.writer().println(msg));

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

    private String extractCharacterName(String content, String description) {
        int start = content.indexOf("name:");
        if (start >= 0) {
            int end = content.indexOf('\n', start);
            if (end > 0) {
                String nameLine = content.substring(start + 5, end).trim();
                if (!nameLine.isBlank()) return nameLine;
            }
        }
        int heading = content.indexOf("# ");
        if (heading >= 0) {
            int headingEnd = content.indexOf('\n', heading);
            if (headingEnd > 0) {
                String headingText = content.substring(heading + 2, headingEnd).trim();
                if (!headingText.isBlank()) return headingText;
            }
        }
        return description.split("[\\s,，、]+")[0];
    }

    private String sanitize(String name) {
        return name.replaceAll("[^\\w\\u4e00-\\u9fff]", "_");
    }
}
