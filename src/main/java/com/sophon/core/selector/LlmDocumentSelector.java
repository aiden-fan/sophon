package com.sophon.core.selector;

import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.unified.*;

import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的文档选择（使用 function call 获取结构化结果）
 * 把项目文件清单发给 LLM，让 LLM 根据用户指令决定需要加载哪些文档
 */
public class LlmDocumentSelector implements DocumentSelector {
    private final LLMProvider llm;

    public LlmDocumentSelector(LLMProvider llm) {
        this.llm = llm;
    }

    @Override
    public SelectionResult select(String userInstruction, List<DocumentMeta> available) {
        // 构建文档清单
        StringBuilder sb = new StringBuilder();
        sb.append("以下是可用的文档列表：\n\n");
        for (DocumentMeta meta : available) {
            String desc = meta.description() != null && !meta.description().isBlank()
                ? " — %s".formatted(meta.description())
                : "";
            sb.append("- %s: %s%s\n".formatted(meta.path(), meta.type(), desc));
        }
        sb.append("\n用户指令: %s\n\n".formatted(userInstruction));
        sb.append("请调用 select_documents 工具，传入需要参考的文档路径列表。");

        // 定义工具：让 LLM 用结构化参数返回选择的文档
        String toolSchema = """
            {
              "type": "object",
              "properties": {
                "selected_paths": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "需要参考的文档路径列表，必须是以下列表中的路径"
                }
              },
              "required": ["selected_paths"]
            }
            """;

        var request = UnifiedChatRequest.builder()
            .messages(List.of(UnifiedMessage.system(sb.toString())))
            .tools(List.of(new UnifiedTool("select_documents", "选择需要参考的文档", toolSchema)))
            .temperature(0.1)
            .maxTokens(256)
            .build();

        try {
            UnifiedChatResponse response = llm.complete(request);

            // 从 tool_calls 中提取结果
            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                for (ToolCall tc : response.toolCalls()) {
                    if ("select_documents".equals(tc.name())) {
                        return parseToolArgs(tc.argsJson(), available);
                    }
                }
            }

            // 如果 LLM 没有返回 tool_call，降级
            return fallbackAll(available);
        } catch (Exception e) {
            return fallbackAll(available);
        }
    }

    @SuppressWarnings("unchecked")
    private SelectionResult parseToolArgs(String argsJson, List<DocumentMeta> available) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            List<String> selected = (List<String>) args.get("selected_paths");

            // 过滤掉无效路径
            List<String> valid = selected.stream()
                .filter(p -> available.stream().anyMatch(m -> m.path().equals(p)))
                .toList();

            if (valid.isEmpty()) return fallbackAll(available);
            return new SelectionResult(valid);
        } catch (Exception e) {
            return fallbackAll(available);
        }
    }

    private SelectionResult fallbackAll(List<DocumentMeta> available) {
        return new SelectionResult(available.stream()
            .filter(m -> !"chapter".equals(m.type()))
            .map(DocumentMeta::path)
            .toList());
    }
}
