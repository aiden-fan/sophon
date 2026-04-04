package com.sophon.skill;

import com.sophon.core.session.SessionManager;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.tool.ToolExecutor;

/**
 * 会话级策略下的技能执行：检查本会话是否禁用技能及依赖工具。
 */
public final class SkillExecutor {

    private final SessionManager sessionManager;
    private final SkillRegistry skillRegistry;
    private final ToolExecutor toolExecutor;

    public SkillExecutor(SessionManager sessionManager, SkillRegistry skillRegistry, ToolExecutor toolExecutor) {
        this.sessionManager = sessionManager;
        this.skillRegistry = skillRegistry;
        this.toolExecutor = toolExecutor;
    }

    public SkillResult run(String sessionId, String skillId, String argumentsJson) {
        Skill skill =
                skillRegistry
                        .get(skillId)
                        .orElseThrow(() -> new IllegalArgumentException("未知技能: " + skillId));
        SessionCapabilityConfig cap =
                sessionManager.getSession(sessionId).orElseThrow().getCapabilities();
        if (!cap.allowsSkill(skillId)) {
            return SkillResult.error("本会话已禁用技能: " + skillId);
        }
        for (String toolName : skill.definition().getRequiredTools()) {
            if (!cap.allowsTool(toolName)) {
                return SkillResult.error("本会话已禁用依赖工具: " + toolName);
            }
        }
        return skill.execute(new SkillInvocation(sessionId, argumentsJson, toolExecutor));
    }
}
