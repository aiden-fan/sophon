package com.sophon.core.selector;

import java.util.List;

/**
 * 文档选择结果
 */
public record SelectionResult(
    List<String> paths  // 选定要加载的文件路径
) { }
