package com.sophon.core.selector;

/**
 * 文档元数据
 */
public record DocumentMeta(
    String path,        // "characters/张三.md"
    String type,        // "character" / "outline" / "world" / "chapter"
    String title,       // "张三"（从 frontmatter 或文件名提取）
    String description  // 文档简要描述，用于 LLM 选文档时参考
) { }
