# Sophon（Local Agent Framework）

> 个人本地智能 Agent 框架：多模型、多模态、Tool/Skill、RAG、MCP、多 Agent 协作（A2A）。  
> 仓库为 **JDK 17 + Maven** 多模块工程：`common`、`server`、`client/cli`。

## 前置条件

- **JDK 17+**
- **Maven 3.8+**（用于构建）

## 构建

```bash
git clone https://github.com/aiden-fan/sophon.git && cd sophon
mvn clean install
```

产物：

- 服务端可执行包：`server/target/sophon-server-1.0.0.jar`
- CLI 可执行包：`client/cli/target/sophon-cli-1.0.0.jar`

## 环境与目录（建议）

```bash
export SOPHON_HOME="$HOME/.sophon"
# 若服务端开启认证（见 ~/.sophon/config/server.yaml）
export SOPHON_TOKEN="your-secret-token"
# 通义千问（DashScope）；未设置时 LLM 会回退到本地 mock，便于开发
export DASHSCOPE_API_KEY="your-dashscope-api-key"
# 可选：OPENAI_API_KEY、ANTHROPIC_API_KEY

mkdir -p ~/.sophon/{config,sessions,plugins,data/{cache,database,logs}}
```

说明：

- 服务端默认 SQLite 库路径：`jdbc:sqlite:${SOPHON_HOME}/data/sophon-runtime.db`（首次启动会自动建目录）。
- 可选配置文件：`~/.sophon/config/server.yaml`（端口、认证、LLM 等），详见 [docs/安全配置与数据持久化.md](docs/安全配置与数据持久化.md)。

## Quick Start

**终端 1 — 启动服务端**

```bash
java -jar server/target/sophon-server-1.0.0.jar
```

默认监听 `http://127.0.0.1:8080`。也可用仓库脚本：`./scripts/start-server.sh`（需先 `mvn package`）。

**终端 2 — CLI**

```bash
# 健康检查
java -jar client/cli/target/sophon-cli-1.0.0.jar health

# 单次对话（未指定会话时自动使用 / 创建「main」）
java -jar client/cli/target/sophon-cli-1.0.0.jar chat "你好"

# 流式 + RAG 参数（与服务端 ChatRequest 字段对应）
java -jar client/cli/target/sophon-cli-1.0.0.jar chat --stream --rag-top-k 3 --rag-min-score 0.08 "介绍一下 Sophon"

# 交互式 REPL（连续对话，不必每次敲完整命令）
java -jar client/cli/target/sophon-cli-1.0.0.jar repl
```

REPL 内可用：`/help`、`/session <id|name>`、`/stream on|off`、`/rag <topK> <minScore>`、`/exit`。  
**上次 REPL 的会话与配置**会保存到 `~/.sophon/config/cli-repl-state.json`（可用 `SOPHON_HOME` 换根目录）；启动时 `--stream-mode auto|on|off`、`-s`、`-u`、`--rag-*` 可覆盖已保存项。

CLI 默认服务地址：环境变量 **`SOPHON_SERVER_URL`**（如 `http://127.0.0.1:8080`），或使用 `-u`。

更完整的启动、停止与脚本说明见 **[docs/使用方式.md](docs/使用方式.md)**。

## 当前能力速览（实现进度以路线图为准）

| 能力 | 说明 |
| ---- | ---- |
| REST API | 健康检查、会话、聊天（含流式 SSE）、知识入库、工具/技能、能力清单等 |
| 持久化 | SQLite + Flyway 迁移 |
| LLM | DashScope 兼容接口（需 `DASHSCOPE_API_KEY`）；失败或无 key 时可回退 mock |
| RAG | 本地向量检索骨架 + `citations[]`；支持请求级 `ragTopK` / `ragMinScore` |

详细接口示例见 **[docs/HTTP接口参考.md](docs/HTTP接口参考.md)**；整体路线见 **[docs/开发计划与路线图.md](docs/开发计划与路线图.md)**。

## 文档导航

**完整技术文档**在 **[docs/](docs/)** 目录。面向 **AI 编码助手** 的索引见仓库根目录 **[llms.txt](llms.txt)**（符合 [llms.txt](https://llmstxt.org/) 约定）。

| | |
| ------------------------------------------ | -------------------- |
| **[docs/文档索引.md](docs/文档索引.md)** | **文档索引**（推荐阅读顺序） |
| [docs/项目概述与架构能力清单.md](docs/项目概述与架构能力清单.md) | 项目概述与架构能力清单 |
| [docs/架构设计.md](docs/架构设计.md) | 架构设计、组件边界、会话模型、编排与横切 |
| [docs/模块划分.md](docs/模块划分.md) | 仓库模块划分 |
| [docs/技术栈与核心特性.md](docs/技术栈与核心特性.md) | 技术栈与核心特性 |
| [docs/安全配置与数据持久化.md](docs/安全配置与数据持久化.md) | 安全、配置、密钥、数据持久化 |
| [docs/工程实践.md](docs/工程实践.md) | 契约与测试 |
| [docs/使用方式.md](docs/使用方式.md) | 使用方式（CLI / 服务端） |
| [docs/HTTP接口参考.md](docs/HTTP接口参考.md) | HTTP REST API 示例 |
| [docs/上下文与A2A与插件.md](docs/上下文与A2A与插件.md) | 上下文、A2A、插件 |
| [docs/开发计划与路线图.md](docs/开发计划与路线图.md) | 统一开发路线（按序号顺序推进） |
| [docs/常见问题.md](docs/常见问题.md) | 常见问题 |
| [docs/贡献指南.md](docs/贡献指南.md) | 贡献指南 |

## 许可证

[MIT License](LICENSE)

## 联系方式

- Issues: [https://github.com/aiden-fan/sophon/issues](https://github.com/aiden-fan/sophon/issues)  
- Discussions: [https://github.com/aiden-fan/sophon/discussions](https://github.com/aiden-fan/sophon/discussions)

如需调整版权信息，可编辑 [LICENSE](LICENSE)。
