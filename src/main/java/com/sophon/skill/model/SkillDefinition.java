package com.sophon.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 自 {@code skill.yaml} 解析的技能契约；可与 Java {@link com.sophon.skill.Skill} 实现绑定。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SkillDefinition {

    private String id;
    private String name = "";
    private String description = "";

    @JsonProperty("minSophonVersion")
    private String minSophonVersion;

    @JsonProperty("requiredTools")
    private List<String> requiredTools = List.of();

    public SkillDefinition() {}

    public SkillDefinition(
            String id,
            String name,
            String description,
            String minSophonVersion,
            List<String> requiredTools) {
        this.id = id;
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.minSophonVersion = minSophonVersion;
        this.requiredTools = requiredTools != null ? List.copyOf(requiredTools) : List.of();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMinSophonVersion() {
        return minSophonVersion;
    }

    public void setMinSophonVersion(String minSophonVersion) {
        this.minSophonVersion = minSophonVersion;
    }

    public List<String> getRequiredTools() {
        return requiredTools;
    }

    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools != null ? List.copyOf(requiredTools) : List.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SkillDefinition that = (SkillDefinition) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(minSophonVersion, that.minSophonVersion)
                && Objects.equals(requiredTools, that.requiredTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, minSophonVersion, requiredTools);
    }
}
