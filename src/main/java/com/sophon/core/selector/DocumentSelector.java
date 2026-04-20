package com.sophon.core.selector;

import java.util.List;

/**
 * 文档选择接口
 * 根据用户指令决定加载哪些文档
 */
public interface DocumentSelector {
    SelectionResult select(String userInstruction, List<DocumentMeta> available);
}
