package com.sophon.model;

import java.time.Instant;
import java.util.Objects;

public final class Session {

    private String id;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    /** 与会话一并持久化；未加载时由存储层填充为 {@link SessionCapabilityConfig#defaultNew()}。 */
    private SessionCapabilityConfig capabilities;

    public Session() {}

    public Session(String id, String title, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SessionCapabilityConfig getCapabilities() {
        return capabilities != null ? capabilities : SessionCapabilityConfig.defaultNew();
    }

    public void setCapabilities(SessionCapabilityConfig capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Session session = (Session) o;
        return Objects.equals(id, session.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
