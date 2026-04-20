package com.sophon.core.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 小说项目路径管理
 */
public class NovelProjectPath {
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

    public Path novelYaml() {
        return projectRoot.resolve("novel.yaml");
    }

    public Path worldSetting() {
        return projectRoot.resolve("world-setting.md");
    }

    public Path outline() {
        return projectRoot.resolve("outline.md");
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
        java.util.List<String> paths = new java.util.ArrayList<>();
        Path root = projectRoot;

        // 根目录文件
        addIfExists(paths, "novel.yaml");
        addIfExists(paths, "world-setting.md");
        addIfExists(paths, "outline.md");

        // characters/
        Path charsDir = charactersDir();
        if (Files.isDirectory(charsDir)) {
            try (Stream<Path> stream = Files.list(charsDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> paths.add("characters/" + p.getFileName()));
            } catch (IOException e) {
                // ignore
            }
        }

        // chapters/
        Path chapDir = chaptersDir();
        if (Files.isDirectory(chapDir)) {
            try (Stream<Path> stream = Files.list(chapDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(p -> paths.add("chapters/" + p.getFileName()));
            } catch (IOException e) {
                // ignore
            }
        }

        // outlines/
        Path outDir = outlinesDir();
        if (Files.isDirectory(outDir)) {
            try (Stream<Path> stream = Files.list(outDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> paths.add("outlines/" + p.getFileName()));
            } catch (IOException e) {
                // ignore
            }
        }

        return paths.stream().sorted().toList();
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
        if ("world-setting.md".equals(relativePath)) return "world";
        if ("novel.yaml".equals(relativePath)) return "meta";
        return "unknown";
    }
}
