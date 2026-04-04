package com.sophon.skill;

/** 技能执行结果（与 Tool 类似，供编排层展示）。 */
public record SkillResult(boolean success, String message) {

    public static SkillResult ok(String message) {
        return new SkillResult(true, message);
    }

    public static SkillResult error(String message) {
        return new SkillResult(false, message);
    }
}
