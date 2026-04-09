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

## 会话级 Agent 绑定（Tool / 知识库）

> **职责划分**：路径与 DTO 在 **`api.*`（Spring）**；校验 Registry/知识库 id、合并默认与会话覆盖、**重建或增量更新会话内 Agent 实例**在 **`core.*`**。以下路径为设计示例，以 OpenAPI 为准。

```bash
# 查询本会话当前绑定的 Tool id 列表与知识库 id 列表
GET /api/v1/sessions/{session_id}/agent-bindings

Response:
{
  "tool_ids": ["code_analyzer", "git_runner"],
  "knowledge_collection_ids": ["kb_docs", "kb_repo"]
}

# 增量：增加绑定（幂等：已存在则忽略或 204）
POST /api/v1/sessions/{session_id}/agent-bindings/tools
Content-Type: application/json
{ "add": ["mcp_search"], "remove": [] }

POST /api/v1/sessions/{session_id}/agent-bindings/knowledge
Content-Type: application/json
{ "add": ["kb_legal"], "remove": ["kb_docs"] }

# 或全量替换（实现可选，与增量二选一或并存）
PUT /api/v1/sessions/{session_id}/agent-bindings
Content-Type: application/json
{
  "tool_ids": ["code_analyzer"],
  "knowledge_collection_ids": ["kb_repo"]
}
```

## 聊天接口

对外 **只使用 Sophon 统一契约**（`parts[]`、统一流式事件类型）；**不包含**各 LLM 厂商原生请求/响应字段。**持久化与会话上下文**与下文 **同一套统一模型**；**仅**在 **`core.llm.providers.*` 的 `LLMClient`** 内完成与 OpenAI/DashScope/Claude 等的 format 映射。

```bash
# 非流式聊天（纯文本简写：仍建议与多模态同一结构）
POST /api/v1/chat
Content-Type: application/json

{
  "session_id": "session_001",
  "stream": false,
  "input": {
    "parts": [
      { "type": "text", "text": "分析这段代码" }
    ]
  }
}

# 非流式 — 多模态输入示例（url / base64 / asset_id 等以 OpenAPI 枚举为准）
POST /api/v1/chat
Content-Type: application/json

{
  "session_id": "session_001",
  "stream": false,
  "input": {
    "parts": [
      { "type": "text", "text": "这张图里有什么？" },
      { "type": "image", "url": "https://example.com/a.png" }
    ]
  }
}

Response（统一结构，可含多模态部件）:
{
  "message_id": "msg_001",
  "output": {
    "parts": [
      { "type": "text", "text": "图中是……" }
    ]
  },
  "usage": { "prompt_tokens": 100, "completion_tokens": 50 }
}

# 流式聊天（Server-Sent Events）— 每条 data 为统一事件，非厂商原始 chunk
POST /api/v1/chat
Content-Type: application/json

{
  "session_id": "session_001",
  "stream": true,
  "input": {
    "parts": [
      { "type": "text", "text": "生成排序算法" }
    ]
  }
}

Response (SSE)，事件 type 为 Sophon 枚举示例:
data: {"type":"delta_text","text":"这是"}
data: {"type":"delta_text","text":"一个"}
data: {"type":"delta_part","part":{"type":"text","text":"分段可携带结构化部件"}}
data: {"type":"tool_call_delta","tool_call_id":"call_1","name":"code_analyzer","arguments_json_fragment":"{\""}
data: {"type":"usage","prompt_tokens":10,"completion_tokens":2}
data: {"type":"done","message_id":"msg_001"}
```

**会话内切换模型**：`session_id` 关联的 `llm.provider` / `llm.model` 变更后，**同一 `input` 形状**仍有效；适配器按当前模型能力做转换；若不支持某 `part` 类型，返回 **`type":"error"`** 事件或 4xx/统一错误体（以 OpenAPI 为准）。

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
