package com.sophon.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 命名能力预设（阶段 14）：与 {@code profiles} 表对应；内容为 {@link SessionCapabilityConfig} 快照。
 */
public final class CapabilityProfile {

    private final String id;
    private final String name;
    private final String capabilitiesJson;
    private final Instant createdAt;

    public CapabilityProfile(String id, String name, String capabilitiesJson, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name != null ? name : "";
        this.capabilitiesJson = capabilitiesJson != null ? capabilitiesJson : "{}";
        this.createdAt = createdAt != null ? createdAt : Instant.EPOCH;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SessionCapabilityConfig resolvedCapabilities() {
        return SessionCapabilityConfig.fromJson(capabilitiesJson);
    }
}
