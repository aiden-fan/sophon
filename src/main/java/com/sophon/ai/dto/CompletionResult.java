package com.sophon.ai.dto;

import java.util.List;

/**
 * 同步补全结果：纯文本（可分思考与正文）、或要求执行本地/远程工具。
 */
public sealed interface CompletionResult permits CompletionResult.Text, CompletionResult.ToolCalls {

    /**
     * @param content 最终对用户可见的正文（对应供应商 {@code message.content}）
     * @param thinking 思考/推理链（对应 {@code reasoning_content} 等）；无则为 {@code null}
     */
    record Text(String content, String thinking) implements CompletionResult {

        public Text(String content) {
            this(content, null);
        }

        public Text {
            if (content == null) {
                content = "";
            }
        }

        /** 与历史代码兼容：返回正文 {@link #content}。 */
        public String text() {
            return content;
        }
    }

    record ToolCalls(List<ToolCall> calls) implements CompletionResult {}
}
