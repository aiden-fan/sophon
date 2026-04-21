---
name: novel-writer
title: 网络小说写作
description: >-
  面向网络小说创作的编排技能：先识别任务类型，再拼装完整写作 prompt（基础模板+任务模板+按需角色+章节窗口大纲），并在写作完成后把设定变更回写到工作目录。
  当用户提出写大纲、建角色、写章节、写章节大纲或改稿需求时使用。
minSophonVersion: "0.1.0"
requiredTools:
  - build_novel_prompt
  - read_file
  - create_file
  - write_file
---

# 网络小说写作

## 调用方式

通过 `invoke_skill` 调用，`skill_id` 使用 **`novel_writer`**。

## 工作目录约定（建议）

```text
<workspace_dir>/
├── prompts/
│   ├── base.md
│   └── tasks/
│       ├── outline.md
│       ├── character.md
│       ├── chapter_outline.md
│       ├── chapter.md
│       └── revise.md
├── story/
│   ├── outline.md
│   ├── characters/
│   │   ├── 主角.md
│   │   └── 女主.md
│   ├── world.md
│   ├── style.md
│   └── chapter_outlines/
│       ├── 第1章.md
│       ├── 第2章.md
│       └── 第3章.md
└── chapters/
    └── 第1章.md
```

## `operation=build_prompt`

- 必填：`task_type`、`user_request`
- 可选：`workspace_dir`、`chapter_id`、`chapter_no`、`character_names`、`extra_context_paths`、`max_chars`
- 作用：返回可直接投喂 LLM 的完整 prompt 文本

关键加载策略：
- 运行时只读取工作目录下的模板与状态文件，不读取项目 resources 模板；`workspace_dir` 未提供时默认 `user.dir`。
- 任务模板：`prompts/tasks/<task_type>.md`
- 章节正文/章节大纲任务要求提供 `chapter_no`
- 章节窗口：当给定 `chapter_no` 时，仅加载 `story/chapter_outlines/第(n-3..n+3)章.md`
- 角色按需：当给定 `character_names` 时，仅加载 `story/characters/<角色名>.md`

模板变量（任务模板中可用）：
- `${user_request}` `${task_type}` `${chapter_no}` `${chapter_id}`
- `${world_setting}` `${story_outline}` `${style}`
- `${characters}` `${chapter_outlines}` `${current_chapter}`

## `operation=sync_state`

- 必填：`path`、`content`
- 可选：`create_parents`、`append`、`create_only`
- 作用：把角色/大纲/章节状态改动写回文件

建议策略：
- 首次创建文件：`create_only=true`
- 已有文件更新：`create_only=false`（默认，走 `write_file`）

## `operation=read_state`

- 必填：`path`
- 作用：读取指定状态文件
