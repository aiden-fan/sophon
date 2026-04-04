package com.sophon.model;

import java.util.Objects;

/** 阶段 17：跨会话 FTS 检索命中一行。 */
public final class MessageSearchHit {

    private final String sessionId;
    private final String messageId;
    private final String contentSnippet;

    public MessageSearchHit(String sessionId, String messageId, String contentSnippet) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.messageId = Objects.requireNonNull(messageId);
        this.contentSnippet = contentSnippet != null ? contentSnippet : "";
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getContentSnippet() {
        return contentSnippet;
    }
}
