package com.sophon.core.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 加载 base prompt 文件
 * 优先从用户项目目录加载，没有则用内置默认
 */
public class PromptLoader {

    /**
     * 加载指定名称的 base prompt
     * @param name 如 "character-base", "outline-base", "write-base"
     * @param userProjectPath 用户项目路径（可为 null）
     * @return prompt 内容字符串
     */
    public static String load(String name, Path userProjectPath) {
        // 1. Try user project prompts/ directory
        if (userProjectPath != null) {
            Path projectPrompt = userProjectPath.resolve("prompts").resolve(name + ".md");
            if (Files.exists(projectPrompt)) {
                try {
                    return Files.readString(projectPrompt);
                } catch (IOException e) {
                    // fallback to built-in
                }
            }
        }

        // 2. Try classpath resources (templates/prompts/)
        try (var is = PromptLoader.class.getResourceAsStream("/templates/prompts/" + name + ".md")) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // fallback
        }

        // 3. Try legacy prompts/ classpath
        try (var is = PromptLoader.class.getResourceAsStream("/prompts/" + name + ".md")) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // fallback
        }

        // 3. Return minimal fallback
        return "你是一个网络小说创作助手。请根据用户的指令完成任务。\n";
    }
}
