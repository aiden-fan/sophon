package com.sophon.server.api.controller;

import com.sophon.server.core.skill.SkillService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping("/{skillName}/execute")
    public Map<String, Object> execute(@PathVariable("skillName") String skillName, @RequestBody(required = false) Map<String, Object> body) {
        String input = body == null ? "" : String.valueOf(body.getOrDefault("input", ""));
        return Map.of("skill", skillName, "output", skillService.run(skillName, input));
    }
}
