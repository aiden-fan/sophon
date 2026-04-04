package com.sophon.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.tool.ToolRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 从 {@code skill.yaml} 加载 {@link SkillDefinition}，并对 {@link ToolRegistry} 做依赖校验。
 */
public final class SkillLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public SkillDefinition loadYaml(Path path) throws IOException {
        return YAML.readValue(path.toFile(), SkillDefinition.class);
    }

    public SkillDefinition loadResource(ClassLoader classLoader, String classpathLocation) throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalArgumentException("找不到 classpath 资源: " + classpathLocation);
            }
            return YAML.readValue(in, SkillDefinition.class);
        }
    }

    /** 校验 {@code requiredTools} 均在全局注册表中。 */
    public void validateRequiredTools(SkillDefinition def, ToolRegistry tools) {
        if (def.getRequiredTools() == null) {
            return;
        }
        for (String name : def.getRequiredTools()) {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("技能 " + def.getId() + " 声明了空的工具依赖");
            }
            if (tools.get(name.trim()).isEmpty()) {
                throw new IllegalStateException("技能 " + def.getId() + " 依赖的工具未注册: " + name);
            }
        }
    }
}
