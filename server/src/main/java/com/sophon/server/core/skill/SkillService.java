package com.sophon.server.core.skill;

import org.springframework.stereotype.Service;

@Service
public class SkillService {

    public String run(String skillName, String input) {
        if ("summarize".equals(skillName)) {
            if (input == null) {
                return "";
            }
            return input.length() <= 100 ? input : input.substring(0, 100) + "...";
        }
        throw new IllegalArgumentException("unknown skill: " + skillName);
    }
}
