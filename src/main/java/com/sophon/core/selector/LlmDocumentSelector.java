package com.sophon.core.selector;

import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.unified.UnifiedChatRequest;
import com.sophon.core.llm.unified.UnifiedChatResponse;
import com.sophon.core.llm.unified.UnifiedMessage;

import java.util.List;

/**
 * 基于 LLM 的文档选择
 * 把项目文件清单发给 LLM，让 LLM 根据用户指令决定需要加载哪些文档
 */
public class LlmDocumentSelector implements DocumentSelector {
    private final LLMProvider llm;

    public LlmDocumentSelector(LLMProvider llm) {
        this.llm = llm;
    }

    @Override
    public SelectionResult select(String userInstruction, List<DocumentMeta> available) {
        // Build a simple list for LLM to choose from
        StringBuilder sb = new StringBuilder();
        sb.append("以下是可用的文档列表：\n\n");
        for (DocumentMeta meta : available) {
            sb.append("- [%s] %s\n".formatted(meta.type(), meta.path()));
        }
        sb.append("\n用户指令: %s\n\n".formatted(userInstruction));
        sb.append("请列出需要参考的所有文档路径，每行一个，只输出路径，不要解释。");

        var request = UnifiedChatRequest.builder()
            .messages(List.of(UnifiedMessage.system(sb.toString())))
            .temperature(0.1)
            .maxTokens(512)
            .build();

        try {
            UnifiedChatResponse response = llm.complete(request);
            String content = response.content();

            // Parse response: extract paths that match available docs
            List<String> selectedPaths = available.stream()
                .map(DocumentMeta::path)
                .filter(content::contains)
                .toList();

            // If LLM returned nothing useful, fall back to loading everything
            if (selectedPaths.isEmpty()) {
                selectedPaths = available.stream()
                    .filter(m -> !"chapter".equals(m.type()))  // skip existing chapters
                    .map(DocumentMeta::path)
                    .toList();
            }

            return new SelectionResult(selectedPaths);
        } catch (Exception e) {
            // Fallback: load everything non-chapter
            return new SelectionResult(available.stream()
                .filter(m -> !"chapter".equals(m.type()))
                .map(DocumentMeta::path)
                .toList());
        }
    }
}
