package com.sophon.cli.command;

import com.sophon.core.init.NovelProjectInitializer;
import com.sophon.core.tool.NovelProjectPath;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.nio.file.Path;

public class NovelCommand {
    private final Terminal terminal;
    private NovelProjectPath projectPath;

    public NovelCommand(Terminal terminal) {
        this.terminal = terminal;
    }

    public void info() {
        // Delegate to tool - print info about current project
        terminal.writer().println("当前项目: " + (projectPath != null ? projectPath.root() : "未打开"));
        terminal.writer().flush();
    }

    public void open(String path) {
        if (path == null || path.isBlank()) {
            terminal.writer().println("用法: /novel open <项目路径>");
            terminal.writer().flush();
            return;
        }
        Path p = Path.of(path).toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(p)) {
            terminal.writer().println("路径不存在: " + p);
            terminal.writer().flush();
            return;
        }
        this.projectPath = new NovelProjectPath(p);
        terminal.writer().println("已打开项目: " + p);
        terminal.writer().flush();
    }

    public void createNew(String description) {
        // Phase 2: simple template init. Phase 3+: use LLM to customize
        if (description == null || description.isBlank()) {
            terminal.writer().println("用法: /new <小说描述>，例如 /new 修仙小说，主角叫张三");
            terminal.writer().flush();
            return;
        }

        // Parse description for title and genre
        String title = extractTitle(description);
        String genre = extractGenre(description);

        Path target = Path.of(System.getProperty("user.dir")).resolve(sanitize(title)).toAbsolutePath().normalize();

        terminal.writer().println("创建项目: " + title);
        terminal.writer().println("路径: " + target);
        terminal.writer().flush();

        NovelProjectPath path = new NovelProjectPath(target);
        new NovelProjectInitializer(path).initialize(title, genre);

        this.projectPath = path;
        terminal.writer().println("项目已创建，使用 /novel info 查看信息，/write 开始创作");
        terminal.writer().flush();
    }

    public NovelProjectPath getProjectPath() {
        return projectPath;
    }

    private String extractTitle(String description) {
        // Simple heuristic: take first meaningful chunk
        return description.replaceAll("，.*$", "").replaceAll(",", "").trim();
    }

    private String extractGenre(String description) {
        String[] genres = {"修仙", "玄幻", "都市", "仙侠", "武侠", "科幻", "奇幻", "历史", "游戏", "军事"};
        for (String g : genres) {
            if (description.contains(g)) return g;
        }
        return "未指定";
    }

    private String sanitize(String name) {
        return name.replaceAll("[^\\w\\u4e00-\\u9fff]", "_");
    }
}
