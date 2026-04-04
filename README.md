# Sophon

## 简介

Sophon 是一套可在本机运行的**个人智能体**骨架：用多会话组织对话，按会话开关 **Tool**（含 MCP 桥接）、**Skill** 与**知识库 / RAG**，并对接阿里云 **Dashscope（通义）** 做流式补全。数据默认落在 **SQLite**，向量检索与消息检索可在本地完成，适合作为「自己的助手」逐步扩展能力，而不必依赖单一聊天网页。

架构上坚持 **CLI 与 Web（WebFlux）共用同一套 application 用例**：编排、持久化与模型调用集中在 `core` / `application` 层，终端和 HTTP 只做交互壳。**默认启动只拉起 Web 服务**；需要终端对话时用 **`--cli`** 或 **`scripts/sophon`** 另开进程（与 Web 共用同一 SQLite 库）。

若你只想**尽快跑通对话**，看下面「快速开始」即可；若要查**规格、阶段、配置、`/` 命令**，见 **[ai.md](ai.md)**（架构导航）与 **`docs/`** 下各专题文档。

## 文档怎么读

| 文档 | 适合谁 |
|------|--------|
| **[README.md](README.md)**（本页） | 想快速上手、跑起来 |
| **[ai.md](ai.md)** | 一页看清架构与技术方案 + **文档地图** |
| **[docs/产品规格.md](docs/产品规格.md)** | 按域的完整功能要求 |
| **[docs/交付阶段.md](docs/交付阶段.md)** | 阶段 1–18、门禁、里程碑要点 |
| **[docs/架构.md](docs/架构.md)** | 分层、Session、包结构、稳定 API |
| **[docs/配置说明.md](docs/配置说明.md)** | 配置键、环境变量、覆盖顺序 |
| **[docs/会话命令.md](docs/会话命令.md)** | 会话能力与 `/` 命令 |
| **[docs/开发与运行.md](docs/开发与运行.md)** | 常用命令、目录结构、开发注意 |
| **[docs/知识库与RAG协作.md](docs/知识库与RAG协作.md)** | 知识库、RAG、Tool / Skill 扩展 |
| **[docs/技术栈.md](docs/技术栈.md)** | 技术选型与 Maven 说明 |

## 快速开始

1. **环境**：JDK **17+**、**Maven 3.8+**  
2. **模型密钥**（通义千问 / Dashscope）：设置环境变量 **`DASHSCOPE_API_KEY`**（勿写入仓库）  
3. **启动主进程（默认仅 Web，不占终端对话）**（需已配置 `DASHSCOPE_API_KEY`）：

```bash
mvn exec:java
```

浏览器访问 **http://localhost:8080/** ；下拉切换会话会加载历史（`GET /api/sessions/{id}/messages`），发送走流式 `POST /api/sessions/{id}/chat/stream`。

**放后台运行**（示例）：

```bash
nohup mvn exec:java > sophon.log 2>&1 &
```

4. **终端里呼出 CLI 对话**（与上面 Web **共用同一数据库文件**，需主进程已启动或至少未独占锁死库；通常 SQLite 可多进程读写）：

```bash
./scripts/sophon
# 或
mvn exec:java -Dexec.args="--cli"
```

**CLI 恢复旧会话**：`./scripts/sophon --session <会话id>` 或 `mvn exec:java -Dexec.args="--cli --session <id>"`；交互内也可用 `/session list`、`/session use <id>`。

可将 `scripts/sophon` 拷到 `PATH`（或 `ln -s`）以便随处执行 `sophon`。

5. **不写库冒烟**（不调用模型）：

```bash
mvn exec:java -Dexec.args="--smoke"
```

6. **测试**：

```bash
mvn test
```

7. **显式只启 Web**（与不带参数等价，兼容旧习惯）：

```bash
mvn exec:java -Dexec.args="--web"
```

更多命令（批测、打包运行等）见 **[docs/开发与运行.md — 常用命令](docs/开发与运行.md#toc-common-commands)**。

## 常用入口

- **`/` 命令**：[docs/会话命令.md](docs/会话命令.md)  
- **配置与覆盖顺序**：[docs/配置说明.md](docs/配置说明.md)  
- **阶段列表**：[docs/交付阶段.md](docs/交付阶段.md)

## 版本

当前构件版本见 `pom.xml`（例如 `0.1.0-alpha-SNAPSHOT`）。
