package com.sophon.core.selector;

import com.sophon.core.llm.LlmLogger;

import java.util.List;

/**
 * 文档选择接口
 * 根据用户指令决定加载哪些文档
 */
public interface DocumentSelector {
    /**
     * @param llmLog 若非 null，文档筛选的 LLM 调用记入该日志（与同一次创作共用同一文件）
     */
    SelectionResult select(String userInstruction, List<DocumentMeta> available, LlmLogger llmLog);

    default SelectionResult select(String userInstruction, List<DocumentMeta> available) {
        return select(userInstruction, available, null);
    }
}
