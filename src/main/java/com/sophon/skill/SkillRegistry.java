package com.sophon.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 进程内技能注册表（与会话级启用集正交）。 */
public final class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public void register(Skill skill) {
        String id = skill.definition().getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("技能 id 不能为空");
        }
        if (skills.containsKey(id)) {
            throw new IllegalStateException("技能已注册: " + id);
        }
        skills.put(id, skill);
    }

    public Optional<Skill> get(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public List<String> ids() {
        return List.copyOf(skills.keySet());
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }
}
