package com.sophon.ai.dto;

import java.util.List;

/**
 * 流式补全中的一帧：可向用户展示的文本片段（带 {@link ModelOutputKind}），或一轮结束时解析出的工具调用列表。
 */
public sealed interface StreamingChunk
        permits StreamingChunk.TextToken, StreamingChunk.ToolCallsFinished, StreamingChunk.Progress {

    /**
     * @param kind {@link ModelOutputKind#FINAL} 表示常规正文；{@link ModelOutputKind#THINKING} 表示思考/推理片段。
     */
    record TextToken(String text, ModelOutputKind kind) implements StreamingChunk {

        /** 未区分类型时视为最终正文。 */
        public TextToken(String text) {
            this(text, ModelOutputKind.FINAL);
        }

        public TextToken {
            if (text == null) {
                throw new IllegalArgumentException("text 不能为 null");
            }
            if (kind == null) {
                throw new IllegalArgumentException("kind 不能为 null");
            }
        }
    }

    record ToolCallsFinished(List<ToolCall> calls) implements StreamingChunk {}

    /** 执行进度事件：用于 CLI/Web 向用户反馈当前节点（如 LLM 调用、工具执行）。 */
    record Progress(String stage, String detail) implements StreamingChunk {

        public Progress {
            if (stage == null || stage.isBlank()) {
                throw new IllegalArgumentException("stage 不能为 null/blank");
            }
            if (detail == null) {
                throw new IllegalArgumentException("detail 不能为 null");
            }
        }
    }
}
