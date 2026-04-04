package com.sophon.core.application;

import com.sophon.core.session.SessionManager;
import com.sophon.model.SessionCapabilityConfig;

/**
 * 会话能力配置用例：与 CLI / Web 共用，持久化经 {@link SessionManager}。
 */
public final class SessionCapabilityService {

    private final SessionManager sessionManager;

    public SessionCapabilityService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public SessionCapabilityConfig getCapabilities(String sessionId) {
        return sessionManager.getSession(sessionId).orElseThrow().getCapabilities();
    }

    public void updateCapabilities(String sessionId, SessionCapabilityConfig config) {
        sessionManager.updateCapabilities(sessionId, config);
    }

    public void setToolDisabled(String sessionId, String toolName, boolean disabled) {
        sessionManager.setToolDisabled(sessionId, toolName, disabled);
    }

    public void resetSessionToolDisables(String sessionId) {
        sessionManager.resetSessionToolDisables(sessionId);
    }

    public void setSkillDisabled(String sessionId, String skillId, boolean disabled) {
        sessionManager.setSkillDisabled(sessionId, skillId, disabled);
    }

    public void resetSessionSkillDisables(String sessionId) {
        sessionManager.resetSessionSkillDisables(sessionId);
    }

    public void attachKnowledgeBase(String sessionId, String kbName) {
        sessionManager.attachKnowledgeBase(sessionId, kbName);
    }

    public void detachKnowledgeBase(String sessionId, String kbName) {
        sessionManager.detachKnowledgeBase(sessionId, kbName);
    }

    public void resetSessionKnowledgeBases(String sessionId) {
        sessionManager.resetSessionKnowledgeBases(sessionId);
    }

    public void setCitationsEnabled(String sessionId, boolean enabled) {
        sessionManager.setCitationsEnabled(sessionId, enabled);
    }

    public void setTrustLevel(String sessionId, String trustLevel) {
        sessionManager.setTrustLevel(sessionId, trustLevel);
    }

    public void setToolRateLimitPerMinute(String sessionId, Integer limitOrNull) {
        sessionManager.setToolRateLimitPerMinute(sessionId, limitOrNull);
    }
}
