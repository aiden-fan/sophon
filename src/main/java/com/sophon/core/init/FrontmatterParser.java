package com.sophon.core.init;

import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 解析 Markdown 文件的 YAML frontmatter (--- ... ---)
 */
public class FrontmatterParser {

    private static final Yaml yaml = new Yaml();

    /**
     * 解析 frontmatter，返回 metadata map（如果没有 frontmatter 返回空 map）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(Path file) {
        try {
            String content = Files.readString(file);
            return parse(content);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String content) {
        if (!content.startsWith("---")) {
            return Map.of();
        }
        int end = content.indexOf("---", 3);
        if (end < 0) {
            return Map.of();
        }
        String yamlContent = content.substring(3, end).trim();
        try {
            Map<String, Object> result = yaml.load(yamlContent);
            return result != null ? result : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 提取 body（去掉 frontmatter 后的 markdown 内容）
     */
    public static String body(String content) {
        if (!content.startsWith("---")) {
            return content;
        }
        int end = content.indexOf("---", 3);
        if (end < 0) {
            return content;
        }
        // skip the second --- and the newline after it
        int bodyStart = content.indexOf('\n', end);
        if (bodyStart < 0) {
            return "";
        }
        return content.substring(bodyStart + 1).stripLeading();
    }
}
