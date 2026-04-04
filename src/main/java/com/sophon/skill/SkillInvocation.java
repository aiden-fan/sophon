package com.sophon.skill;

import com.sophon.tool.ToolExecutor;

/**
 * 单次技能执行上下文：会话、参数 JSON、以及受会话策略约束的 {@link ToolExecutor}。
 */
public record SkillInvocation(String sessionId, String argumentsJson, ToolExecutor toolExecutor) {}
