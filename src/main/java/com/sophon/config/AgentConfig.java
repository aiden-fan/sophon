package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent 编排参数；阶段 3 仅使用轮次上限，后续与 Tool 循环、resilience 对齐。
 */
public class AgentConfig {

    /** 单次用户输入后，至多执行几次「模型调用」（含 tool 跟进轮）；阶段 3 无 Tool 时为 1；阶段 5+ 建议 ≥4。 */
    @JsonProperty("max-rounds")
    private int maxRounds = 8;

    /** 为 false 时不向模型声明 tools，且不执行工具分支（与空注册表效果类似）。 */
    @JsonProperty("tools-enabled")
    private boolean toolsEnabled = true;

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    public void setToolsEnabled(boolean toolsEnabled) {
        this.toolsEnabled = toolsEnabled;
    }
}
