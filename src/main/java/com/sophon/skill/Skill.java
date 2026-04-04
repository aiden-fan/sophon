package com.sophon.skill;

import com.sophon.skill.model.SkillDefinition;

/** 可执行技能：定义来自 {@code skill.yaml} 与/或 Java 实现。 */
public interface Skill {

    SkillDefinition definition();

    SkillResult execute(SkillInvocation invocation);
}
