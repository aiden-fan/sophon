package com.sophon.tool;

/**
 * 工具风险等级：高危工具需用户确认或 {@link com.sophon.model.SessionCapabilityConfig} 提升信任后方可自动执行。
 */
public enum ToolRisk {

    /** 默认可直接执行（仍受会话禁用与限流约束）。 */
    STANDARD,

    /** 读敏感路径、写删、外呼等；执行前须确认或 elevated 信任。 */
    HIGH
}
