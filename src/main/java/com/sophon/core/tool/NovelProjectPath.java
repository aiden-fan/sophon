package com.sophon.core.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 小说项目路径管理
 */
public class NovelProjectPath {
    private static final Pattern UNSAFE_FILE_CHARS = Pattern.compile("[^\\w\\u4e00-\\u9fff-]");
    private final Path projectRoot;

    public NovelProjectPath(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public Path root() {
        return projectRoot;
    }

    public Path resolve(String relativePath) {
        return projectRoot.resolve(relativePath).normalize();
    }

    /**
     * 仅允许解析到项目根目录内，防止路径越权。
     */
    public Path resolveInsideProject(String relativePath) {
        Path resolved = resolve(relativePath);
        if (!resolved.startsWith(projectRoot)) {
            throw new IllegalArgumentException("非法路径，超出项目目录: " + relativePath);
        }
        return resolved;
    }

    /**
     * 规范化文件名，仅保留中文、字母、数字、下划线和中划线。
     */
    public String sanitizeFileName(String rawName, String fallback) {
        if (rawName == null) return fallback;
        String cleaned = UNSAFE_FILE_CHARS.matcher(rawName.trim()).replaceAll("_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    public Path worldSetting() {
        return projectRoot.resolve("world-setting.md");
    }

    public Path outline() {
        return projectRoot.resolve("outline.md");
    }

    public Path storyProgress() {
        return projectRoot.resolve("story-progress.md");
    }

    public Path charactersDir() {
        return projectRoot.resolve("characters");
    }

    public Path chaptersDir() {
        return projectRoot.resolve("chapters");
    }

    public Path outlinesDir() {
        return projectRoot.resolve("outlines");
    }

    /**
     * 扫描项目所有文档，返回相对路径列表
     */
    public java.util.List<String> scanAllDocuments() {
        List<String> paths = new ArrayList<>();

        // 根目录文件
        addIfExists(paths, "world-setting.md");
        addIfExists(paths, "outline.md");
        addIfExists(paths, "story-progress.md");
        addIfExists(paths, "structure.md");

        scanDirectory(paths, charactersDir(), ".md");
        scanDirectory(paths, outlinesDir(), ".md");
        scanDirectory(paths, chaptersDir(), ".txt");

        return paths.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private void scanDirectory(List<String> paths, Path baseDir, String extension) {
        if (!Files.isDirectory(baseDir)) return;
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                .map(projectRoot::relativize)
                .map(Path::toString)
                .map(p -> p.replace('\\', '/'))
                .forEach(paths::add);
        } catch (IOException e) {
            throw new RuntimeException("扫描目录失败: " + baseDir, e);
        }
    }

    private void addIfExists(java.util.List<String> paths, String relative) {
        if (Files.exists(projectRoot.resolve(relative))) {
            paths.add(relative);
        }
    }

    /**
     * 识别文档类型
     */
    public static String docType(String relativePath) {
        if (relativePath.startsWith("characters/")) return "character";
        if (relativePath.startsWith("chapters/")) return "chapter";
        if (relativePath.startsWith("outlines/")) return "chapter-outline";
        if ("outline.md".equals(relativePath)) return "outline";
        if ("story-progress.md".equals(relativePath)) return "story-progress";
        if ("world-setting.md".equals(relativePath)) return "world";
        if ("structure.md".equals(relativePath)) return "structure";
        return "unknown";
    }
}
