# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Sophon Novel — 一个辅助创作网络小说的本地 CLI 应用。通过 JLine 提供 REPL 交互，AI 采用 Dashscope（通义千问）作为 LLM 后端。

## 技术栈

- **Java 17**
- **Maven 3.8+**
- **JLine 3** — 终端 UI
- **Dashscope SDK** — 通义千问 LLM 接入
- **Jackson** — JSON 解析
- **SnakeYAML** — YAML frontmatter 解析
- **Reactive Streams** — LLM 流式输出

## 常用命令

```bash
# 编译
mvn compile

# 运行（开发模式，需要 Maven）
mvn exec:java

# 打包（生成包含所有依赖的 fat jar）
mvn package

# 运行 jar 包
java -jar target/sophon-novel-0.1.0-SNAPSHOT.jar

# 指定 API key 运行
DASHSCOPE_API_KEY=your-key java -jar target/sophon-novel-0.1.0-SNAPSHOT.jar

# 检查代码风格
mvn checkstyle:check

# 清理构建产物
mvn clean
```

## 架构概览

```
CliApp (JLine REPL)
  └── CommandRouter          # 命令分发：/new, /novel, /write, /list, /chapter, /character, /outline
       │
       ├── LLMProviderFactory        # 创建 LLM provider（mock / dashscope）
       ├── ToolRegistry              # 注册表：读写文档、章节、项目信息
       ├── CreationPipeline           # 创作流程编排
       │     ├── DocumentSelector    # LLM 分析项目文件清单，选择相关文档
       │     ├── ContextBuilder       # 策略化拼接文档 → system prompt
       │     ├── PromptRenderer       # base prompt + ${} 关键词替换
       │     └── LLMProvider          # 统一模型 → 厂商格式 → 流式生成
       │
       └── Tool (builtin)
             ├── ListDocumentsTool    # 列出项目所有文档
             ├── ReadDocumentsTool    # 批量读取多个文档
             ├── WriteChapterTool     # 写入章节文件
             ├── ReadChapterTool      # 读取指定章节
             ├── NovelProjectInfoTool # 获取项目元信息
             └── EchoTool             # 调试用
```

## 核心工作流

每次创作请求（如 `/write "写第一章"`）的完整流程：

1. **扫描** — `NovelProjectPath.scanAllDocuments()` 列出所有可用文档元数据
2. **选择** — `LlmDocumentSelector` 将文件清单发给 LLM，由 LLM 决定加载哪些文档
3. **读取** — `ReadDocumentsTool` 批量读取选定文档内容
4. **组装** — `ContextBuilder` + `PromptRenderer` 按策略拼接：base prompt + 关键词替换 + 选中文档
5. **生成** — `LLMProvider.complete()` 请求 LLM，支持 tool call 循环（最多 10 轮）
6. **写入** — `OutputHandler` 将结果写入章节文件

## 关键包结构

```
com.sophon/
├── SophonApplication.java      # main 入口
├── cli/
│   ├── CliApp.java             # JLine REPL 主循环
│   ├── CommandRouter.java      # 斜杠命令路由
│   └── command/
│       ├── NovelCommand.java   # /novel open/info, /new
│       ├── WriteCommand.java   # /write — 章节创作
│       ├── CharacterCommand.java # /character — 角色创建
│       └── OutlineCommand.java # /outline — 大纲创建
├── core/
│   ├── llm/
│   │   ├── LLMProvider.java            # 统一 LLM 接口
│   │   ├── LLMProviderFactory.java     # provider 工厂
│   │   ├── unified/                    # 统一 DTO（角色/消息/请求/响应/工具）
│   │   └── providers/
│   │       ├── DashscopeProvider.java  # 通义千问实现
│   │       └── MockProvider.java       # 开发/测试 mock
│   ├── tool/
│   │   ├── Tool.java                   # 工具接口
│   │   ├── ToolRegistry.java           # 注册表
│   │   ├── NovelProjectPath.java       # 项目路径解析 + 文档扫描 + 类型识别
│   │   └── builtin/                    # 内置工具实现
│   ├── selector/
│   │   ├── DocumentSelector.java       # 文档选择接口
│   │   ├── LlmDocumentSelector.java    # 基于 LLM 的实现
│   │   ├── DocumentMeta.java           # 文档元数据
│   │   └── SelectionResult.java        # 选择结果
│   ├── context/
│   │   ├── ContextBuilder.java         # 上下文组装接口
│   │   ├── DefaultContextBuilder.java  # 默认实现（按类型分组+排序）
│   │   └── ContextSection.java         # 上下文片段
│   ├── pipeline/
│   │   ├── CreationPipeline.java       # 创作流程编排
│   │   └── ChapterResult.java          # 创作结果
│   └── init/
│       ├── NovelProjectInitializer.java # 项目初始化
│       ├── FrontmatterParser.java       # YAML frontmatter 解析
│       ├── PromptLoader.java            # prompt 模板加载
│       └── PromptRenderer.java          # prompt 关键词替换
└── config/
    ├── AppConfig.java                   # 应用配置
    └── AiConfig.java                    # AI 配置
```

## 小说项目文件结构

```
~/my-novel/
├── novel.yaml              # 项目元信息（title, genre, description, style）
├── world-setting.md        # 世界观设定（修炼体系、地理、势力）
├── outline.md              # 总大纲（简介、主线、章节规划）
├── structure.md            # 结构设定（可选）
├── prompts/                # 自定义 prompt 模板（可选）
│   ├── character-base.md
│   ├── outline-base.md
│   └── write-base.md
├── characters/             # 角色档案（.md）
│   └── 张三.md
├── outlines/               # 章节大纲（.md）
│   └── chapter-001.md
└── chapters/               # 章节正文（.txt）
    └── chapter-001-穿越.txt
```

所有文档统一 Markdown，头部用 YAML frontmatter 存元数据。章节正文使用 `.txt` 纯文本格式。

## 开发约定

- 使用中文注释和命名，贴合小说创作场景
- 新功能先在 `feature/novel` 分支开发，完成后合并到 `main`
- 提交信息使用 `feat:` / `fix:` / `refactor:` 前缀
- 除 `providers/*` 外，任何代码不得 import LLM SDK 的类（LLM 差异封装在 provider 层）
- 每次创作是一次性流程，不维护对话上下文/记忆

## LLM Provider 边界

- `LLMProvider` 是统一接口，内部代码只认 `UnifiedChatRequest` / `UnifiedChatResponse`
- `DashscopeProvider` 负责将统一模型转为通义千问格式，反之亦然
- 工具调用在 `CreationPipeline.generateWithToolCall()` 中处理：LLM 响应 → 解析 ToolCall → 执行 → 注入对话 → 循环
