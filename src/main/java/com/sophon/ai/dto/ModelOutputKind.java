package com.sophon.ai.dto;

/**
 * 模型输出文本片段的语义类型，用于区分思考链与最终对用户内容等。
 */
public enum ModelOutputKind {

    /** 推理/思考过程（如供应商 {@code reasoning_content}、思维链）。 */
    THINKING,

    /** 最终对用户展示的正文（如 {@code content}）。 */
    FINAL
}
