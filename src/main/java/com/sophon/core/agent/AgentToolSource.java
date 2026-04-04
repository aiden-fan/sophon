package com.sophon.core.agent;

import com.sophon.tool.ToolDefinition;

import java.util.List;

/**
 * 按会话向模型额外声明的 tools（如 Skill 暴露的 function、知识库检索工具），
 * 与 {@link com.sophon.tool.ToolRegistry} 合并；同步与流式路径共用同一列表。
 */
@FunctionalInterface
public interface AgentToolSource {

    List<ToolDefinition> extraToolDefinitions(String sessionId);
}
