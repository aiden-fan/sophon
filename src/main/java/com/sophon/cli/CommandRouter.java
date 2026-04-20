package com.sophon.cli;

import com.sophon.cli.command.NovelCommand;
import com.sophon.cli.command.OutlineCommand;
import com.sophon.cli.command.WriteCommand;
import com.sophon.cli.command.CharacterCommand;
import com.sophon.core.context.DefaultContextBuilder;
import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.LLMProviderFactory;
import com.sophon.core.llm.unified.UnifiedChatRequest;
import com.sophon.core.llm.unified.UnifiedMessage;
import com.sophon.core.llm.unified.UnifiedStreamEvent;
import com.sophon.core.tool.ToolRegistry;
import com.sophon.core.tool.builtin.*;
import com.sophon.core.tool.NovelProjectPath;
import org.jline.terminal.Terminal;
import org.reactivestreams.Publisher;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class CommandRouter {
    private final Terminal terminal;
    private final LLMProviderFactory llmFactory;
    private final ToolRegistry toolRegistry;
    private NovelProjectPath projectPath;
    private NovelCommand novelCommand;
    private WriteCommand writeCommand;
    private CharacterCommand characterCommand;
    private OutlineCommand outlineCommand;

    public CommandRouter(Terminal terminal) {
        this.terminal = terminal;
        this.llmFactory = new LLMProviderFactory();
        this.toolRegistry = new ToolRegistry();
        this.novelCommand = new NovelCommand(terminal);
        LLMProvider llm = llmFactory.getDefault();
        this.writeCommand = new WriteCommand(terminal, llm, toolRegistry);
        this.characterCommand = new CharacterCommand(terminal, llm, toolRegistry);
        this.outlineCommand = new OutlineCommand(terminal, llm, toolRegistry);
    }

    public void execute(String command, String args) {
        switch (command) {
            case "help" -> cmdHelp();
            case "novel" -> cmdNovel(args);
            case "new" -> cmdNew(args);
            case "write" -> cmdWrite(args);
            case "list" -> cmdList();
            case "info" -> cmdInfo();
            case "chapter" -> cmdChapter(args);
            case "character" -> cmdCharacter(args);
            case "outline" -> cmdOutline(args);
            default -> terminal.writer().println("未知命令: /" + command + "，输入 /help 查看帮助");
        }
        terminal.writer().flush();
    }

    public void chat(String message) {
        if (projectPath != null) {
            // If a project is open, treat chat as a write command
            cmdWrite(message);
        } else {
            cmdChat(message);
        }
    }

    private void cmdHelp() {
        if (projectPath == null) {
            terminal.writer().println("""
                可用命令：
                  /help                - 显示帮助
                  /new <描述>          - 创建新项目，如 /new 修仙小说主角叫张三
                  /novel open <路径>   - 打开现有项目
                  /quit /exit          - 退出

                创建/打开项目后，按以下流程创作：
                  1. /character  创建角色档案
                  2. /outline    生成章节大纲
                  3. /write      AI 创作章节正文
                也可以直接输入文字开始写作（等同于 /write）
                """);
        } else {
            terminal.writer().println("""
                创作流程：
                  1. /character <描述>   - 创建角色，如 /character 主角，修仙天才
                  2. /outline <指令>     - 创建章节大纲，如 /outline 第3章 主角初遇女主
                  3. /write <指令>       - AI 创作章节，如 /write 写第一章 主角穿越

                其他命令：
                  /help                - 显示帮助
                  /novel info          - 查看项目信息
                  /list                - 列出所有文档
                  /chapter read <编号>  - 阅读指定章节
                  /quit /exit          - 退出

                直接输入文字等同于 /write
                """);
        }
        terminal.writer().flush();
    }

    private void cmdNovel(String args) {
        String[] parts = args.split("\\s+", 2);
        String sub = parts[0];
        switch (sub) {
            case "open" -> {
                String path = parts.length > 1 ? parts[1] : null;
                novelCommand.open(path);
                if (novelCommand.getProjectPath() != null) {
                    projectPath = novelCommand.getProjectPath();
                    registerProjectTools(projectPath);
                }
            }
            case "info" -> cmdInfo();
            default -> terminal.writer().println("用法: /novel open <路径> 或 /novel info");
        }
        terminal.writer().flush();
    }

    private void cmdNew(String args) {
        novelCommand.createNew(args);
        if (novelCommand.getProjectPath() != null) {
            projectPath = novelCommand.getProjectPath();
            registerProjectTools(projectPath);
            writeCommand = new WriteCommand(terminal, llmFactory.getDefault(), toolRegistry);
        }
        terminal.writer().flush();
    }

    private void cmdWrite(String args) {
        writeCommand.execute(args, projectPath);
    }

    private void cmdList() {
        if (projectPath == null) {
            terminal.writer().println("请先打开项目");
            return;
        }
        var tool = new ListDocumentsTool(projectPath);
        var result = tool.execute(Map.of());
        terminal.writer().println(result.content());
        terminal.writer().flush();
    }

    private void cmdInfo() {
        if (projectPath == null) {
            terminal.writer().println("未打开项目");
            return;
        }
        var tool = new NovelProjectInfoTool(projectPath);
        var result = tool.execute(Map.of());
        if (result.isError()) {
            terminal.writer().println(result.content());
        } else {
            terminal.writer().println(result.content());
        }
        terminal.writer().flush();
    }

    private void cmdChapter(String args) {
        if (projectPath == null) {
            terminal.writer().println("请先打开项目");
            terminal.writer().flush();
            return;
        }
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2 || !"read".equals(parts[0])) {
            terminal.writer().println("用法: /chapter read <编号>");
            terminal.writer().flush();
            return;
        }
        try {
            int num = Integer.parseInt(parts[1]);
            var tool = new ReadChapterTool(projectPath);
            var result = tool.execute(Map.of("chapter", num));
            terminal.writer().println(result.content());
        } catch (NumberFormatException e) {
            terminal.writer().println("章节编号必须是数字");
        }
        terminal.writer().flush();
    }

    private void cmdCharacter(String args) {
        if (projectPath == null) {
            terminal.writer().println("请先打开项目: /novel open <路径> 或创建: /new <描述>");
            terminal.writer().flush();
            return;
        }
        characterCommand.execute(args, projectPath);
    }

    private void cmdOutline(String args) {
        outlineCommand.execute(args, projectPath);
    }

    private void cmdChat(String message) {
        var provider = llmFactory.getDefault();
        if ("mock".equals(provider.providerName())) {
            var response = provider.complete(UnifiedChatRequest.builder()
                .messages(List.of(UnifiedMessage.user(message)))
                .build());
            terminal.writer().println(response.content());
        } else {
            terminal.writer().print("[");
            Publisher<UnifiedStreamEvent> stream = provider.stream(UnifiedChatRequest.builder()
                .messages(List.of(UnifiedMessage.user(message)))
                .build());
            stream.subscribe(new org.reactivestreams.Subscriber<UnifiedStreamEvent>() {
                org.reactivestreams.Subscription sub;
                @Override public void onSubscribe(org.reactivestreams.Subscription s) { this.sub = s; s.request(Long.MAX_VALUE); }
                @Override public void onNext(UnifiedStreamEvent event) {
                    if (event.isDone()) {
                        terminal.writer().println("]");
                    } else {
                        terminal.writer().print(event.delta());
                        terminal.writer().flush();
                    }
                }
                @Override public void onError(Throwable t) {
                    terminal.writer().println("\n[错误] " + t.getMessage());
                    terminal.writer().flush();
                }
                @Override public void onComplete() { terminal.writer().flush(); }
            });
        }
    }

    private void registerProjectTools(NovelProjectPath path) {
        toolRegistry.register(new ListDocumentsTool(path));
        toolRegistry.register(new ReadDocumentsTool(path));
        toolRegistry.register(new WriteChapterTool(path));
        toolRegistry.register(new ReadChapterTool(path));
        toolRegistry.register(new NovelProjectInfoTool(path));
    }
}
