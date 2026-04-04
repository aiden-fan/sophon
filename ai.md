# Sophon — 架构导航

> **分工**：本文件概括**项目定位、整体架构与技术方案**，并指向 `docs/` 下的功能文档。适合快速建立心智模型；给 AI 编程工具时可 **@本文件 + 按需 @ 子文档**（如 `@docs/架构.md`），避免单次上下文过长。

## 文档地图


| 文档                                     | 内容                                             |
| -------------------------------------- | ---------------------------------------------- |
| [docs/产品规格.md](docs/产品规格.md)           | 产品能力规格（按域的需求表）                                 |
| [docs/交付阶段.md](docs/交付阶段.md)           | 交付阶段 1–18、门禁原则、里程碑要点                           |
| [docs/架构.md](docs/架构.md)               | 核心概念、分层职责、Session 持久化、包结构、横切、稳定 API、模块树、数据所有权  |
| [docs/技术栈.md](docs/技术栈.md)             | 技术栈、Reactor/Web/Maven 等选型                      |
| [docs/会话命令.md](docs/会话命令.md)           | 会话级能力、`SessionCapabilityConfig`、`/` 命令与 Web 对齐 |
| [docs/配置说明.md](docs/配置说明.md)           | 配置优先级、日志、AI、system prompt、Agent、韧性、环境变量        |
| [docs/知识库与RAG协作.md](docs/知识库与RAG协作.md) | 知识库 / RAG / Tool / Skill 协作、扩展与 MCP            |
| [docs/开发与运行.md](docs/开发与运行.md)         | Quick Start、项目结构、测试约定、开发注意与扩展点                 |
| [docs/文档排版约定.md](docs/文档排版约定.md)       | 维护文档时的排版约定                                     |


人类读者**上手运行**见根目录 [README.md](README.md)。

---

## 项目概述

Sophon 是个人智能体项目：多会话、**按会话**启用 Tool（含 MCP）、Skill 与知识库 / RAG，对接 **Dashscope（通义）** 等模型。数据默认 **SQLite**；向量检索与编排集中在服务端逻辑，**CLI 与 Web（WebFlux）共用 `core/application` 用例**，避免两套编排。

**功能全集**以 [产品规格](docs/产品规格.md) 为准；**开发顺序与测试门禁**以 [交付阶段](docs/交付阶段.md) 为准；所列能力须在 `core/application` 与既定分层落地，不得仅在 `cli` 包实现。

---

## 整体架构与技术方案

### 原则

- **模块化、接口优先**：核心能力通过接口暴露，实现可替换。
- **单一编排入口**：对话、RAG、工具循环的业务编排集中在 `**core/application`** + `**AgentEngine**` + `**knowledge.RAGEngine**`；`cli` / `web` 只做适配。
- **依赖方向**：`application` → Skill / AgentEngine → Tool、knowledge、`ai`；传输层不承载业务状态机。

### 技术要点

- **流式与异步**：统一 **Project Reactor**（`Flux` / `Mono`）；`AIProvider` 流式输出；阻塞 IO（如 JDBC）放在 `**Schedulers.boundedElastic()`**。
- **Web**：推荐 **Spring Boot 3 + WebFlux**，SSE 与 CLI 订阅同一用例返回的流。
- **会话能力**：每会话持久化 `**SessionCapabilityConfig`**（Tool/Skill/KB 子集、citations、trust、system prompt 等），与 [会话命令](docs/会话命令.md) 及 REST **同构**。
- **构建**：单模块 Maven，alpha 阶段可保持一 `artifactId`；依赖与可执行 JAR 策略见 [技术栈 — Maven](docs/技术栈.md#toc-maven-deps)。

### 分层与模块（摘要）

`SessionManager` 管会话生命周期与能力配置持久化；`ConversationManager` 管消息与上下文窗口；`AgentEngine` 管 LLM + Tool 状态机并尊重会话级 Tool 过滤与 RAG 范围；`RAGEngine` 管检索增强与引用结构。横切（审计、用量、Tracing）建议收于 `core/observability`。完整表格与包树见 [架构说明](docs/架构.md)。

---

## 排版与维护

编写或修改上述 Markdown 时，遵循 [文档排版约定](docs/文档排版约定.md)。