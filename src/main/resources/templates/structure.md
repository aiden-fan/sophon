# ${title} — 项目文档结构

本文档描述项目的文件结构和文档类型，供 AI 在决定加载哪些文档时参考。

## 根目录文件

- `novel.yaml` — 项目元信息（标题、类型、描述、风格）
- `world-setting.md` — 世界观设定（修炼体系、地理、势力）
- `outline.md` — 总大纲（简介、主线、章节规划）
- `structure.md` — 本文档（目录结构说明）

## 目录

### characters/ — 角色档案（.md 文件）
每个文件一个角色，包含 YAML frontmatter（name, role, status）和角色描述（外貌、性格、修为、背景、人际关系）。

### outlines/ — 章节大纲（.md 文件）
文件命名：`chapter-NNN.md`，包含 YAML frontmatter（chapter, title, word_count_target, key_characters）和章节场景规划。

### chapters/ — 章节正文（.txt 文件）
文件命名：`chapter-NNN-标题.txt`，纯文本格式，包含 YAML frontmatter（chapter, title, word_count）。

### prompts/ — 自定义 prompt 模板（.md 文件）
- `character-base.md` — 角色生成 prompt
- `outline-base.md` — 大纲生成 prompt
- `write-base.md` — 章节写作 prompt
用户可以修改这些文件来自定义 AI 生成行为。

## 创作流程

1. **创建角色**（`/character <描述>`）→ 生成 `characters/角色名.md`
2. **创建大纲**（`/outline 第N章 <描述>`）→ 生成 `outlines/chapter-NNN.md`
3. **写作**（`/write 第N章 <描述>`）→ 生成 `chapters/chapter-NNN-标题.txt`

每次创作时，AI 会先扫描项目所有文档，根据用户指令选择最相关的文档加载到上下文。

## 如何新增自定义文件

用户可以在项目目录下创建任意 `.md` 文件（如 `势力设定.md`、`地图.md` 等）。
这些文件会在 `scanAllDocuments` 中被扫描到，AI 可以根据需要决定是否加载。
