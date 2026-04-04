# 知识库、RAG、Tool 与 Skill 协作

与 [产品规格 — RAG 与知识](产品规格.md)、[架构](架构.md) 一致。

<a id="toc-collab"></a>

## 协作架构

依赖倒置：**Skill / application / AgentEngine** 向下依赖「工具执行」与「知识检索」两个端口，而非把 Tool 与 Knowledge 混为同一实现层。

```
┌─────────────────────────────────────────────┐
│     User Interaction (CLI / Web)            │
│     → 仅调用 core/application 用例           │
└──────────────┬──────────────────────────────┘
               │
┌──────────────▼──────────────────────────────┐
│  Skill Layer + AgentEngine + application    │
│  - 场景编排、工具循环、RAG 调用入口          │
│  - SKILL.md / skill.yaml 描述技能契约       │
└─────┬──────────────────────┬─────────────────┘
      │                      │
      │ ToolExecutor         │ RAGEngine / KnowledgeManager
      │ (副作用、外部系统)    │ (只读检索、向量、文档)
      ▼                      ▼
┌─────────────┐      ┌──────────────────────┐
│ Tool Layer  │      │ Knowledge / RAG      │
│ Local + MCP │      │ 检索、索引、生成编排  │
└─────────────┘      └──────────────────────┘
```

**协作优势**：知识增强、能力扩展、智能检索与工具在编排层组合、依赖可声明（Skill 依赖 Tool 缺失时须明确报错）。

<a id="toc-rag-pipeline"></a>

## RAG（检索增强生成）工作流程

```
1. 用户输入问题
       ↓
2. application / Skill / AgentEngine 接收并编排
       ↓
3. 从**当前会话已绑定**的知识库检索相关文档
   - 语义检索 (向量相似度)
   - 混合检索 (语义 + 关键词，若实现)
       ↓
4. 将检索结果作为上下文（如注入仅对本请求生效的 system 片段）
       ↓
5. 使用 AI 模型生成回答（支持**流式**输出）
       ↓
6. (按需) 调用 Tool；高危 Tool 需经**确认或信任级别**；**审计**
       ↓
7. 返回最终答案；若开启 **citations**，**附带引用列表**
```

当前实现中，`RAGEngine#augmentForChat` 在已有 `LlmMessage` 列表上插入含检索块的 system 片段，**不改变**持久化历史；检索范围取自本会话 `SessionCapabilityConfig` 中已绑定的知识库名。

**RAG 配置示例：**

```yaml
rag:
  enabled: true
  knowledge_base: "my-docs"
  retrieval:
    method: "hybrid"
    top_k: 5
    score_threshold: 0.7
  generation:
    model: "dashscope-qwen-max"
    max_tokens: 2000
    temperature: 0.7
  citations:
    enabled: true   # 与会话级 citationsEnabled 组合，具体优先级在实现中约定并文档化
```

## 管理知识库（与当前代码对齐）

`KnowledgeManager` 提供文本入库与向量检索（实现细节以源码为准）：

```java
// 将整段文本切块、嵌入后写入 kbName；documentKey 用于生成稳定 chunk id 前缀
knowledgeManager.ingestText("kb-name", "my-doc", "全文内容…");

// 语义检索，返回 VectorSearchHit 列表
List<VectorSearchHit> hits = knowledgeManager.search("kb-name", "查询内容", 5);
```

编排层在对话前可调用 `RAGEngine#augmentForChat(sessionId, messages)`，将会话绑定库上的检索结果并入发给模型的消息列表。

**知识库 YAML 示例**（与配置扩展一致时）：

```yaml
knowledge:
  bases:
    - name: "my-docs"
      type: "local"
      embedding: "dashscope-text-embedding-v2"
      chunk_size: 500
      chunk_overlap: 50
      retrieval:
        type: "vector"
        top_k: 5
        score_threshold: 0.7
```

## 添加新的 Tool

**本地工具：**

1. 在 `tool/` 包下创建新的 Tool 类，继承 `LocalTool`
2. 实现 `execute()` 方法
3. 在 `ToolManager` 中注册新工具
4. 在配置文件中添加工具配置

**MCP 远程工具：**

1. 在配置文件中添加 MCP 服务器配置
2. `MCPTool` 连接 MCP 服务器并加载可用工具
3. 通过 `ToolExecutor` 统一调用

### MCP 工具配置示例

```yaml
mcp:
  servers:
    - name: "filesystem"
      type: "stdio"
      command: "npx"
      args: ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/files"]
    - name: "brave-search"
      type: "stdio"
      command: "npx"
      args: ["-y", "@modelcontextprotocol/server-brave-search"]
      env:
        BRAVE_API_KEY: "${BRAVE_API_KEY}"
```

**连接与资源策略**：stdio + npx 有进程开销；宜支持 **连接复用**、**并发上限与超时**；可选 **懒连接**（首次调用再拉起）。

## 添加新的 Skill（从目录加载）

1. 在 `src/main/resources/skills/` 或配置的技能目录下创建新技能目录
2. 按规范创建 `SKILL.md`（及可选 `skill.yaml`）
3. `SkillLoader` 发现并加载；`SkillManager` 统一管理
4. Skill 实现中可调用 `ToolExecutor`

### 技能目录规范

```
skill-name/
├── SKILL.md               # 技能定义（人类可读，必需）
├── skill.yaml             # 可选：机器可读清单（推荐）
├── impl/
│   ├── SkillNameImpl.java
│   └── scripts/
│       └── skill.py
└── resources/             # 可选
```

**推荐**：机器字段（名称、版本、参数 schema、依赖工具）放在 YAML；`SkillMarkdownParser` 应对缺失/非法字段给出**可定位错误**。

### 技能加载机制

1. `SkillLoader` 扫描配置目录
2. 发现含 `SKILL.md` 的目录；**优先** `skill.yaml` 或 Front Matter
3. 注册前校验声明的 Tool 是否在 `ToolRegistry` 中
4. **热加载**：元数据可热更；**Java 类**动态加载等同不可信代码，默认策略建议分级（内置 vs 用户目录）；脚本须 **超时与隔离**。

### SKILL.md 定义格式（摘要）

```markdown
# 技能名称

## 描述
…

## 依赖工具
| 工具名称 | 工具类型 | 用途 |
|----------|----------|------|
| file-reader | local | 读取文件 |

## 参数
| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|

## 实现
- 类型: java
- 类名: com.sophon.skill.impl.SkillNameImpl
```

## Skill 与 Tool、知识库结合

**直接调用 Tool：**

```java
ToolResult result = toolExecutor.execute("file-reader", params);
```

**Tool 读知识库（依赖只读端口，避免绑死 `KnowledgeManager`）：** 可定义 `KnowledgeQueryPort` 等接口（与 [架构](架构.md) 一致）；以下为示意代码。

```java
public class DocumentAnalyzer extends LocalTool {
    private final KnowledgeQueryPort knowledgeQuery;
    public ToolResult execute(Map<String, Object> params) {
        String content = knowledgeQuery.getDocument((String) params.get("doc_id"));
        // …
    }
}
```

**编排层：先检索再调 Tool**（示例思路）：

```java
List<VectorSearchHit> hits = knowledgeManager.search("kb-name", "相关主题", 5);
for (VectorSearchHit h : hits) {
    toolExecutor.execute("text-analyzer", Map.of("text", h.content()));
}
```

---

[← 回到 ai.md 导航](../ai.md)
