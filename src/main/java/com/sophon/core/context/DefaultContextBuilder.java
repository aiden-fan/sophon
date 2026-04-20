package com.sophon.core.context;

import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.init.PromptRenderer;
import com.sophon.core.llm.unified.UnifiedMessage;
import com.sophon.core.tool.NovelProjectPath;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认上下文组装实现：
 * 按文档类型分组 → 按固定顺序排序 → 拼接为 system prompt
 *
 * 顺序: 世界观 → 角色 → 大纲 → 章节大纲 → 已有章节
 */
public class DefaultContextBuilder implements ContextBuilder {

    private static final Map<String, Integer> TYPE_ORDER = Map.of(
        "world", 1,
        "character", 2,
        "outline", 3,
        "chapter-outline", 4,
        "chapter", 5
    );

    @Override
    public List<UnifiedMessage> build(Map<String, String> documents, String userInstruction,
                                      PromptRenderer renderer, String promptName) {
        // Group documents by type
        Map<String, List<String>> byType = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            String type = classifyDoc(path, content);
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(formatDoc(path, content));
        }

        // Sort types by order
        List<ContextSection> sections = byType.entrySet().stream()
            .map(e -> new ContextSection(
                typeLabel(e.getKey()),
                String.join("\n\n", e.getValue()),
                TYPE_ORDER.getOrDefault(e.getKey(), 99)
            ))
            .sorted(Comparator.comparingInt(ContextSection::order))
            .toList();

        // 加载对应类型的 base prompt（含关键词替换）
        String basePrompt = renderer.render(promptName);
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(basePrompt).append("\n\n");

        // 追加选中的文档内容
        for (ContextSection section : sections) {
            systemPrompt.append("=== %s ===\n\n%s\n\n".formatted(section.title(), section.content()));
        }

        systemPrompt.append("=== 用户指令 ===\n%s\n".formatted(userInstruction));

        return List.of(
            UnifiedMessage.system(systemPrompt.toString()),
            UnifiedMessage.user("请根据以上设定和指令，创作内容。")
        );
    }

    private String classifyDoc(String path, String content) {
        return NovelProjectPath.docType(path);
    }

    private String formatDoc(String path, String content) {
        String body = FrontmatterParser.body(content);
        return "**文件: " + path + "**\n\n" + (body.isBlank() ? content : body);
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "world" -> "世界观设定";
            case "character" -> "角色设定";
            case "outline" -> "总大纲";
            case "chapter-outline" -> "章节大纲";
            case "chapter" -> "已有章节";
            case "meta" -> "项目信息";
            default -> type;
        };
    }
}
