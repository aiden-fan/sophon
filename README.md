# Sophon

## 简介

Sophon 是一套可在本机运行的**个人智能体**骨架：用多会话组织对话，按会话开关 **Tool**（含 MCP 桥接）、**Skill** 与**知识库 / RAG**，并对接阿里云 **Dashscope（通义）** 做流式补全。数据默认落在 **SQLite**，向量检索与消息检索可在本地完成，适合作为「自己的助手」逐步扩展能力，而不必依赖单一聊天网页。

架构上坚持 **CLI 与 Web（WebFlux）共用同一套 application 用例**：编排、持久化与模型调用集中在 `core` / `application` 层，终端和 HTTP 只做交互壳。这样改一处逻辑，两种入口行为一致，也方便写测试与后续换模型、换存储实现。

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
3. **运行交互 CLI**：

```bash
mvn exec:java
```

4. **不写库冒烟**（不调用模型）：

```bash
mvn exec:java -Dexec.args="--smoke"
```

5. **测试**：

```bash
mvn test
```

更多命令（Web、批测、打包运行等）见 **[docs/开发与运行.md — 常用命令](docs/开发与运行.md#toc-common-commands)**。

## 常用入口

- **`/` 命令**：[docs/会话命令.md](docs/会话命令.md)  
- **配置与覆盖顺序**：[docs/配置说明.md](docs/配置说明.md)  
- **阶段列表**：[docs/交付阶段.md](docs/交付阶段.md)

## 版本

当前构件版本见 `pom.xml`（例如 `0.1.0-alpha-SNAPSHOT`）。
