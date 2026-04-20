package com.sophon.core.context;

/**
 * 上下文片段（对应一个文档或一组同类文档）
 */
public record ContextSection(
    String title,       // "角色设定"
    String content,     // 渲染后的文本
    int order           // 排序权重
) { }
