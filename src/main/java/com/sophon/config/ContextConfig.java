package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 阶段 15：发往模型的上下文窗口（按字符估算，近似 token 控制）。 */
public class ContextConfig {

    /** 单条会话上下文最大字符数（含 user/assistant/tool 等映射后的文本）；0 或负数表示不限制。 */
    @JsonProperty("max-chars")
    private int maxChars = 48_000;

    /** 发生裁剪时是否在 system 侧追加说明（避免「静默截断」）。 */
    @JsonProperty("truncation-notice")
    private boolean truncationNotice = true;

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public boolean isTruncationNotice() {
        return truncationNotice;
    }

    public void setTruncationNotice(boolean truncationNotice) {
        this.truncationNotice = truncationNotice;
    }
}
