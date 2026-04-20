package com.sophon.core.init;

import com.sophon.core.tool.NovelProjectPath;

import java.io.IOException;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;

/**
 * 小说项目初始化：将 templates/ 目录树完整复制到目标项目目录，
 * 对支持变量替换的文件执行 ${key} 替换。
 */
public class NovelProjectInitializer {
    private static final String TEMPLATES_ROOT = "templates";

    // 需要变量替换的文件
    private static final Set<String> VAR_SUBSTITUTION_FILES = Set.of(
        "novel.yaml",
        "outline.md",
        "world-setting.md",
        "structure.md",
        "characters/主角.md"
    );

    private final NovelProjectPath projectPath;

    public NovelProjectInitializer(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    public void initialize(String title, String genre) {
        Map<String, String> vars = Map.of(
            "title", title,
            "genre", genre,
            "name", "主角"
        );

        try {
            Path root = projectPath.root();
            Files.createDirectories(root);
            Files.createDirectories(projectPath.charactersDir());
            Files.createDirectories(projectPath.chaptersDir());
            Files.createDirectories(projectPath.outlinesDir());
            Files.createDirectories(root.resolve("prompts"));

            copyTree(vars);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("项目初始化失败", e);
        }
    }

    /**
     * 将 templates/ 目录树复制到项目根目录
     */
    private void copyTree(Map<String, String> vars) throws IOException, URISyntaxException {
        URL templateUrl = getClass().getClassLoader().getResource(TEMPLATES_ROOT);
        if (templateUrl == null) {
            throw new IOException("模板目录不存在: " + TEMPLATES_ROOT);
        }

        Path templatePath = Path.of(templateUrl.toURI());

        Files.walkFileTree(templatePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relative = templatePath.relativize(file).toString();
                Path target = projectPath.root().resolve(relative);
                Files.createDirectories(target.getParent());

                String content = Files.readString(file, StandardCharsets.UTF_8);

                if (VAR_SUBSTITUTION_FILES.contains(relative)) {
                    for (var entry : vars.entrySet()) {
                        content = content.replace("${" + entry.getKey() + "}", entry.getValue());
                    }
                }

                Files.writeString(target, content, StandardCharsets.UTF_8);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
