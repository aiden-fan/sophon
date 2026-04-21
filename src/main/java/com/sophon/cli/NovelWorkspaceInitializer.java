package com.sophon.cli;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 初始化网络小说工作目录：按约定创建 prompts/story/chapters 结构与默认模板文件。
 */
public final class NovelWorkspaceInitializer {

    private static final String TEMPLATE_ROOT = "templates/novel-workspace";
    private static final String MANIFEST = TEMPLATE_ROOT + "/manifest.txt";

    public record InitResult(Path root, int createdFiles, int skippedFiles) {}

    public InitResult initialize(Path targetDir) {
        if (targetDir == null) {
            throw new IllegalArgumentException("初始化目录不能为空");
        }
        Path root = targetDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            List<String> relativeFiles = loadManifest();
            int created = 0;
            int skipped = 0;
            for (String rel : relativeFiles) {
                Path p = root.resolve(rel).normalize();
                if (!p.startsWith(root)) {
                    throw new IllegalArgumentException("检测到非法模板路径: " + rel);
                }
                Path parent = p.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (Files.exists(p)) {
                    skipped++;
                    continue;
                }
                copyTemplateFile(rel, p);
                created++;
            }
            return new InitResult(root, created, skipped);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("初始化失败: " + e.getMessage(), e);
        }
    }

    private static List<String> loadManifest() {
        try (InputStream in = NovelWorkspaceInitializer.class.getClassLoader().getResourceAsStream(MANIFEST)) {
            if (in == null) {
                throw new IllegalArgumentException("缺少模板清单资源: " + MANIFEST);
            }
            List<String> out = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    out.add(t);
                }
            }
            if (out.isEmpty()) {
                throw new IllegalArgumentException("模板清单为空: " + MANIFEST);
            }
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取模板清单失败: " + e.getMessage(), e);
        }
    }

    private static void copyTemplateFile(String relativePath, Path target) {
        String resourcePath = TEMPLATE_ROOT + "/" + relativePath;
        try (InputStream in =
                NovelWorkspaceInitializer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("缺少模板文件资源: " + resourcePath);
            }
            Files.copy(in, target);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("复制模板文件失败: " + relativePath + " — " + e.getMessage(), e);
        }
    }
}
