package com.sophon.core.context;

import com.sophon.core.init.PromptRenderer;
import com.sophon.core.llm.unified.UnifiedMessage;

import java.util.List;
import java.util.Map;

/**
 * 上下文组装接口
 */
public interface ContextBuilder {
    /**
     * @param documents 文档路径 → 文档完整内容
     * @param userInstruction 用户指令
     * @param renderer prompt 关键词替换渲染器
     * @param promptName base prompt 名称（如 "character-base"）
     * @return 组装好的消息列表
     */
    List<UnifiedMessage> build(
        Map<String, String> documents,
        String userInstruction,
        PromptRenderer renderer,
        String promptName
    );
}
