package com.sophon.core.init;

import com.sophon.core.tool.NovelProjectPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 加载 base prompt 并替换关键词为实际项目内容
 *
 * 支持的关键词：
 * ${world_setting}      - world-setting.md 正文
 * ${outline}            - outline.md 正文
 * ${characters}         - characters/*.md 所有角色正文拼接
 * ${outline_chapter_N}  - outlines/chapter-NNN.md 指定章节大纲
 * ${novel_info}         - novel.yaml 内容
 * ${chapter_N}          - chapters/ 下第N章正文
 */
public class PromptRenderer {

    private final NovelProjectPath projectPath;

    public PromptRenderer(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * 加载 prompt 并替换所有 ${...} 关键词
     */
    public String render(String promptName) {
        String prompt = PromptLoader.load(promptName, projectPath.root());
        return resolve(prompt);
    }

    /**
     * 对任意模板字符串做关键词替换
     */
    public String resolve(String template) {
        String result = template;
        for (var entry : buildContext().entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private Map<String, String> buildContext() {
        Map<String, String> ctx = new LinkedHashMap<>();

        // ${novel_info}
        ctx.put("${novel_info}", readRaw(projectPath.novelYaml()));

        // ${world_setting}
        ctx.put("${world_setting}", readBody(projectPath.worldSetting()));

        // ${outline}
        ctx.put("${outline}", readBody(projectPath.outline()));

        // ${characters}
        ctx.put("${characters}", readAllCharacters());

        // ${outline_chapter_N} - scan all outlines
        Path outDir = projectPath.outlinesDir();
        if (Files.isDirectory(outDir)) {
            try (Stream<Path> stream = Files.list(outDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        String key = "${outline_" + filename.replace(".md", "").replace("-", "_") + "}";
                        ctx.put(key, readBody(p));
                    });
            } catch (IOException ignored) {
            }
        }

        // ${chapter_N} - scan all chapters
        Path chapDir = projectPath.chaptersDir();
        if (Files.isDirectory(chapDir)) {
            try (Stream<Path> stream = Files.list(chapDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        String key = "${chapter_" + filename.replace(".md", "").replace("-", "_") + "}";
                        ctx.put(key, readBody(p));
                    });
            } catch (IOException ignored) {
            }
        }

        return ctx;
    }

    private String readBody(Path path) {
        String raw = readRaw(path);
        if (raw == null) return "(无内容)";
        return FrontmatterParser.body(raw);
    }

    private String readRaw(Path path) {
        if (path == null || !Files.exists(path)) return null;
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "(读取失败)";
        }
    }

    private String readAllCharacters() {
        Path charsDir = projectPath.charactersDir();
        if (!Files.isDirectory(charsDir)) return "(无角色文档)";
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> stream = Files.list(charsDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .forEach(p -> {
                    String body = readBody(p);
                    sb.append("## ").append(p.getFileName()).append("\n\n").append(body).append("\n\n");
                });
        } catch (IOException e) {
            return "(读取失败)";
        }
        return sb.isEmpty() ? "(无角色文档)" : sb.toString();
    }
}
