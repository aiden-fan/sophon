package com.sophon.model;

/**
 * 对话消息角色；阶段 1 仅持久化，不参与 LLM 编排。
 */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}
