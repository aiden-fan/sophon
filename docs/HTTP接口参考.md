# Sophon HTTP API 参考（REST）

本文档与 **[文档索引](文档索引.md)**、仓库 **[README](../README.md)** 配套；便于与后续 **OpenAPI** 规范同步维护。路径、字段以最终实现为准。

## 会话管理

```bash
# 创建会话
POST /api/v1/sessions
Content-Type: application/json

{
  "name": "Coding Assistant",
  "llm": {
    "provider": "dashscope",
    "model": "qwen-max"
  },
  "tools": ["code_analyzer", "git_runner"]
}

Response:
{
  "id": "session_001",
  "name": "Coding Assistant",
  "created_at": "2026-04-08T10:00:00Z"
}

# 获取会话列表
GET /api/v1/sessions

# 获取会话详情
GET /api/v1/sessions/{session_id}

# 删除会话
DELETE /api/v1/sessions/{session_id}
```

## 聊天接口

```bash
# 非流式聊天
POST /api/v1/chat
Content-Type: application/json

{
  "session_id": "session_001",
  "message": "分析这段代码",
  "stream": false
}

# 流式聊天（Server-Sent Events）
POST /api/v1/chat
Content-Type: application/json

{
  "session_id": "session_001",
  "message": "生成排序算法",
  "stream": true
}

Response (SSE):
data: {"type":"chunk","content":"这是"}
data: {"type":"chunk","content":"一个"}
data: {"type":"done","message_id":"msg_001"}
```

## 工具管理

```bash
# 列出可用工具
GET /api/v1/tools

# 执行工具
POST /api/v1/tools/{tool_name}/execute
Content-Type: application/json

{
  "session_id": "session_001",
  "input": {...}
}
```

## 插件管理

```bash
# 列出插件
GET /api/v1/plugins

# 加载插件
POST /api/v1/plugins/load
Content-Type: multipart/form-data

file: @my-plugin.jar

# 卸载插件
DELETE /api/v1/plugins/{plugin_id}
```

## 统计信息

```bash
# 获取会话统计
GET /api/v1/sessions/{session_id}/stats

Response:
{
  "session_id": "session_001",
  "total_messages": 150,
  "total_tokens": 45000,
  "total_calls": 10,
  "avg_response_time": 2500,
  "start_time": "2026-04-08T10:00:00Z"
}
```

## A2A Agent 管理

```bash
# 获取所有 Agent 列表
GET /api/v1/agents

# 注册新 Agent
POST /api/v1/agents
Content-Type: application/json

{
  "id": "coding_agent",
  "name": "代码分析专家",
  "role": "code_analyzer",
  "llm": {
    "provider": "dashscope",
    "model": "qwen-max"
  },
  "tools": ["code_analyzer", "file_reader"],
  "skills": ["code_review", "refactoring"],
  "collaboration": {
    "mode": "taskflow",
    "capabilities": ["code_analysis", "code_generation"]
  }
}

Response:
{
  "id": "coding_agent",
  "name": "代码分析专家",
  "status": "active",
  "registered_at": "2026-04-08T10:00:00Z"
}

# 注销 Agent
DELETE /api/v1/agents/{agent_id}

# 获取 Agent 详情
GET /api/v1/agents/{agent_id}
```

## A2A 协作接口

```bash
# 创建协作会话
POST /api/v1/a2a/sessions
Content-Type: application/json

{
  "name": "代码分析协作会话",
  "agents": ["coding_agent", "review_agent", "test_agent"],
  "workflow": "code_analysis",
  "coordination_mode": "taskflow"
}

# 向协作会话发送任务
POST /api/v1/a2a/sessions/{session_id}/tasks
Content-Type: application/json

{
  "task": "分析这段代码并生成测试用例",
  "parameters": {
    "code_snippet": "public class Calculator { ... }"
  }
}

# 查看协作状态
GET /api/v1/a2a/sessions/{session_id}/status

Response:
{
  "session_id": "a2a_001",
  "status": "running",
  "agents": {
    "coding_agent": {
      "status": "completed",
      "result": "代码分析完成"
    },
    "review_agent": {
      "status": "running",
      "progress": 50
    },
    "test_agent": {
      "status": "pending"
    }
  },
  "current_step": "code_review"
}
```
