package com.sophon.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;

/**
 * 本会话能力视图：与 {@link Session} 一并持久化；阶段 6 起 Tool/提示；阶段 12 起 trustLevel；阶段 13 起 Tool 速率上限。
 */
public final class SessionCapabilityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Set<String> disabledToolNames;
    private final String systemPrompt;
    private final Set<String> disabledSkillNames;
    private final Set<String> attachedKnowledgeBaseNames;
    private final boolean citationsEnabled;
    /** {@code standard} 或 {@code elevated}（高危 Tool 免确认）。 */
    private final String trustLevel;
    /** 每分钟本会话 Tool 调用上限；{@code null} 表示不限制。 */
    private final Integer toolRateLimitPerMinute;

    public SessionCapabilityConfig() {
        this(Set.of(), null, Set.of(), Set.of(), true, "standard", null);
    }

    @JsonCreator
    public SessionCapabilityConfig(
            @JsonProperty("disabledToolNames") Set<String> disabledToolNames,
            @JsonProperty("systemPrompt") String systemPrompt,
            @JsonProperty("disabledSkillNames") Set<String> disabledSkillNames,
            @JsonProperty("attachedKnowledgeBaseNames") Set<String> attachedKnowledgeBaseNames,
            @JsonProperty("citationsEnabled") Boolean citationsEnabled,
            @JsonProperty("trustLevel") String trustLevel,
            @JsonProperty("toolRateLimitPerMinute") Integer toolRateLimitPerMinute) {
        this.disabledToolNames = disabledToolNames == null ? Set.of() : Set.copyOf(disabledToolNames);
        this.systemPrompt = systemPrompt;
        this.disabledSkillNames = disabledSkillNames == null ? Set.of() : Set.copyOf(disabledSkillNames);
        this.attachedKnowledgeBaseNames =
                attachedKnowledgeBaseNames == null ? Set.of() : Set.copyOf(attachedKnowledgeBaseNames);
        this.citationsEnabled = citationsEnabled == null || citationsEnabled;
        this.trustLevel =
                trustLevel == null || trustLevel.isBlank() ? "standard" : trustLevel.trim().toLowerCase();
        this.toolRateLimitPerMinute = toolRateLimitPerMinute;
    }

    public static SessionCapabilityConfig defaultNew() {
        return new SessionCapabilityConfig(Set.of(), null, Set.of(), Set.of(), true, "standard", null);
    }

    public Set<String> getDisabledToolNames() {
        return disabledToolNames;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Set<String> getDisabledSkillNames() {
        return disabledSkillNames;
    }

    public Set<String> getAttachedKnowledgeBaseNames() {
        return attachedKnowledgeBaseNames;
    }

    public boolean isCitationsEnabled() {
        return citationsEnabled;
    }

    public String getTrustLevel() {
        return trustLevel;
    }

    public Integer getToolRateLimitPerMinute() {
        return toolRateLimitPerMinute;
    }

    public boolean allowsTool(String toolName) {
        return !disabledToolNames.contains(toolName);
    }

    public boolean allowsSkill(String skillId) {
        return !disabledSkillNames.contains(skillId);
    }

    public SessionCapabilityConfig withSystemPrompt(String promptOrNull) {
        return new SessionCapabilityConfig(
                disabledToolNames,
                promptOrNull,
                disabledSkillNames,
                attachedKnowledgeBaseNames,
                citationsEnabled,
                trustLevel,
                toolRateLimitPerMinute);
    }

    public SessionCapabilityConfig withDisabledToolNames(Set<String> names) {
        return new SessionCapabilityConfig(
                names,
                systemPrompt,
                disabledSkillNames,
                attachedKnowledgeBaseNames,
                citationsEnabled,
                trustLevel,
                toolRateLimitPerMinute);
    }

    public SessionCapabilityConfig withDisabledSkillNames(Set<String> names) {
        return new SessionCapabilityConfig(
                disabledToolNames,
                systemPrompt,
                names,
                attachedKnowledgeBaseNames,
                citationsEnabled,
                trustLevel,
                toolRateLimitPerMinute);
    }

    public SessionCapabilityConfig withAttachedKnowledgeBaseNames(Set<String> names) {
        return new SessionCapabilityConfig(
                disabledToolNames,
                systemPrompt,
                disabledSkillNames,
                names,
                citationsEnabled,
                trustLevel,
                toolRateLimitPerMinute);
    }

    public SessionCapabilityConfig withCitationsEnabled(boolean enabled) {
        return new SessionCapabilityConfig(
                disabledToolNames,
                systemPrompt,
                disabledSkillNames,
                attachedKnowledgeBaseNames,
                enabled,
                trustLevel,
                toolRateLimitPerMinute);
    }

    public SessionCapabilityConfig withTrustLevel(String levelOrNull) {
        String t = levelOrNull == null || levelOrNull.isBlank() ? "standard" : levelOrNull;
        return new SessionCapabilityConfig(
                disabledToolNames,
                systemPrompt,
                disabledSkillNames,
                attachedKnowledgeBaseNames,
                citationsEnabled,
                t,
                toolRateLimitPerMinute);
    }

    public SessionCapabilityConfig withToolRateLimitPerMinute(Integer limitOrNull) {
        return new SessionCapabilityConfig(
                disabledToolNames,
                systemPrompt,
                disabledSkillNames,
                attachedKnowledgeBaseNames,
                citationsEnabled,
                trustLevel,
                limitOrNull);
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 SessionCapabilityConfig 失败", e);
        }
    }

    public static SessionCapabilityConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return defaultNew();
        }
        try {
            return JSON.readValue(json, SessionCapabilityConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 SessionCapabilityConfig 失败: " + json, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SessionCapabilityConfig that = (SessionCapabilityConfig) o;
        return citationsEnabled == that.citationsEnabled
                && Objects.equals(disabledToolNames, that.disabledToolNames)
                && Objects.equals(systemPrompt, that.systemPrompt)
                && Objects.equals(disabledSkillNames, that.disabledSkillNames)
                && Objects.equals(attachedKnowledgeBaseNames, that.attachedKnowledgeBaseNames)
                && Objects.equals(trustLevel, that.trustLevel)
                && Objects.equals(toolRateLimitPerMinute, that.toolRateLimitPerMinute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                disabledToolNames,
                systemPrompt,
                disabledSkillNames,
                attachedKnowledgeBaseNames,
                citationsEnabled,
                trustLevel,
                toolRateLimitPerMinute);
    }
}
