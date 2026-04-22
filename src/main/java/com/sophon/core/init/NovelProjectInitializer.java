package com.sophon.core.init;

import com.sophon.core.tool.NovelProjectPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 小说项目初始化：将 templates/ 目录树完整复制到目标项目目录，
 * 对支持变量替换的文件执行 ${key} 替换。
 */
public class NovelProjectInitializer {
    private static final String TEMPLATES_ROOT = "templates/";

    // 需要变量替换的文件
    private static final Set<String> VAR_SUBSTITUTION_FILES = Set.of(
        "outline.md",
        "world-setting.md",
        "structure.md",
        "story-progress.md"
    );

    private final NovelProjectPath projectPath;

    public NovelProjectInitializer(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    public void initialize(String title, String genre) {
        Map<String, String> vars = Map.of(
            "title", title,
            "genre", genre
        );

        try {
            copyTree(vars);
        } catch (IOException e) {
            throw new RuntimeException("项目初始化失败", e);
        }
    }

    /**
     * 将 classpath 下 templates/ 目录树复制到项目根目录。
     * 兼容 JAR 包内资源（不能用 Path.of 访问 jar 内文件）。
     */
    private void copyTree(Map<String, String> vars) throws IOException {
        // 用 JAR 文件方式读取，兼容 fat jar 和开发模式
        var classLoader = getClass().getClassLoader();

        // 获取 JAR 文件或目录路径
        var resourceUrl = classLoader.getResource(TEMPLATES_ROOT);
        if (resourceUrl == null) {
            // 开发模式：尝试从 classpath 逐个读取已知文件
            copyFromClasspath(vars);
            return;
        }

        // JAR 模式：从 jar:file:// URL 中提取 jar 路径
        String urlStr = resourceUrl.toString();
        if (urlStr.startsWith("jar:")) {
            // jar:file:/path/to/app.jar!/templates/
            String jarPath = urlStr.substring("jar:file:".length(), urlStr.indexOf("!"));
            try (JarFile jar = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(TEMPLATES_ROOT) || entry.isDirectory()) continue;

                    String relative = name.substring(TEMPLATES_ROOT.length());
                    Path target = projectPath.root().resolve(relative);
                    Files.createDirectories(target.getParent());

                    String content;
                    try (InputStream is = classLoader.getResourceAsStream(name)) {
                        if (is == null) continue;
                        content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }

                    if (VAR_SUBSTITUTION_FILES.contains(relative)) {
                        for (var e : vars.entrySet()) {
                            content = content.replace("${" + e.getKey() + "}", e.getValue());
                        }
                    }

                    Files.writeString(target, content, StandardCharsets.UTF_8);
                }
            }
        } else {
            // 开发模式
            copyFromClasspath(vars);
        }
    }

    /**
     * 开发模式：从 classpath 逐个读取已知模板文件。
     */
    private void copyFromClasspath(Map<String, String> vars) throws IOException {
        String[] knownFiles = {
            "outline.md",
            "world-setting.md",
            "story-progress.md",
            "structure.md",
            "characters/主角.md",
            "prompts/character-base.md",
            "prompts/outline-base.md",
            "prompts/write-base.md"
        };

        var classLoader = getClass().getClassLoader();
        for (String file : knownFiles) {
            String resourceName = TEMPLATES_ROOT + file;
            try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
                if (is == null) continue;
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                Path target = projectPath.root().resolve(file);
                Files.createDirectories(target.getParent());

                if (VAR_SUBSTITUTION_FILES.contains(file)) {
                    for (var e : vars.entrySet()) {
                        content = content.replace("${" + e.getKey() + "}", e.getValue());
                    }
                }

                Files.writeString(target, content, StandardCharsets.UTF_8);
            }
        }
    }
}
