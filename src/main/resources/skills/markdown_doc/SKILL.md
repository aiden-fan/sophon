---
name: markdown-doc
title: Markdown 文档
description: >-
  将 Markdown 新建或写入本地文件或读取已有 .md（create_file 仅新建；write_file 覆盖/追加；read_file 读取）。
  在用户需要生成说明文档、笔记、README 片段或持久化对话中的 Markdown 时使用；新建可用 create_file（标准风险）；
  覆盖/追加需 write_file（高危，需 elevated 或终端确认）。
minSophonVersion: "0.1.0"
requiredTools:
  - read_file
  - create_file
  - write_file
---

# Markdown 文档

## 调用方式

通过内置技能 **`markdown_doc`**（`invoke_skill`，`skill_id` 为 `markdown_doc`）执行；不要假设存在其他别名。

## 参数（JSON）

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation` | string | `create`（仅当文件不存在时新建）、`write`（覆盖写）、`append`（追加，或与 `write` + `append:true`）、`read` |
| `path` | string | 文件路径；相对路径相对 JVM 工作目录 |
| `content` | string | `create` / `write` / `append` 时的 UTF-8 正文（`read` 不需要；`create` 可省略表示空文件） |
| `append` | boolean | 与 `operation:"write"` 联用时为 true 表示追加 |
| `create_parents` | boolean | 为 true 时自动创建父目录（写路径时） |

示例：

```json
{"operation":"create","path":"docs/note.md","content":"# 标题\n","create_parents":true}
```

```json
{"operation":"write","path":"docs/note.md","content":"# 标题\n","create_parents":true}
```

```json
{"operation":"read","path":"docs/note.md"}
```

## 约束

- **新建**优先用 `operation:"create"` + `create_file`：文件已存在时会失败，避免误覆盖；一般无需 elevated。
- `write_file` 为高危工具：无终端时需在会话中执行 `/trust elevated`，否则会被拒绝。
- 大文件受工具单文件大小上限约束；超长内容应分块或换路径。
