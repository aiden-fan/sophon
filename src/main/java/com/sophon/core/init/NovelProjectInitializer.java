package com.sophon.core.init;

import com.sophon.core.tool.NovelProjectPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 小说项目初始化：创建目录结构 + 模板文件
 */
public class NovelProjectInitializer {
    private final NovelProjectPath projectPath;

    public NovelProjectInitializer(NovelProjectPath projectPath) {
        this.projectPath = projectPath;
    }

    public void initialize(String title, String genre) {
        try {
            Path root = projectPath.root();
            Files.createDirectories(root);
            Files.createDirectories(projectPath.charactersDir());
            Files.createDirectories(projectPath.chaptersDir());
            Files.createDirectories(projectPath.outlinesDir());
            Path promptsDir = root.resolve("prompts");
            Files.createDirectories(promptsDir);

            writeResource("templates/novel.yaml", projectPath.novelYaml(),
                Map.of("title", title, "genre", genre));
            writeResource("templates/outline.md", projectPath.outline(),
                Map.of("title", title));
            writeResource("templates/world-setting.md", projectPath.worldSetting(),
                Map.of("title", title));
            // 创建项目目录结构说明文档，LLM 选文档时用来了解有哪些文件类型
            writeResource("templates/structure.md", projectPath.resolve("structure.md"),
                Map.of("title", title));

            writeResource("templates/character.md", projectPath.charactersDir().resolve("主角.md"),
                Map.of("name", "主角"));

            // Copy base prompts to project prompts/ directory
            writePrompt("character-base.md", promptsDir.resolve("character-base.md"));
            writePrompt("outline-base.md", promptsDir.resolve("outline-base.md"));
            writePrompt("write-base.md", promptsDir.resolve("write-base.md"));
        } catch (IOException e) {
            throw new RuntimeException("项目初始化失败", e);
        }
    }

    private void writeResource(String resourcePath, Path target, java.util.Map<String, String> vars)
            throws IOException {
        // Read from classpath resources
        String content;
        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                content = defaultTemplate(resourcePath, vars);
            } else {
                content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        // Simple variable substitution
        for (var entry : vars.entrySet()) {
            content = content.replace("${" + entry.getKey() + "}", entry.getValue());
        }

        Files.writeString(target, content, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String defaultTemplate(String resourcePath, java.util.Map<String, String> vars) {
        return switch (resourcePath) {
            case "templates/novel.yaml" -> """
                ---
                title: ${title}
                genre: ${genre}
                description: ""
                style: ""
                created: 2026-04-20
                ---
                """.strip();
            case "templates/outline.md" -> """
                ---
                total_chapters: 0
                current_chapter: 1
                ---
                # ${title}

                ## 简介


                ## 主线


                ## 章节规划
                ### 第一卷
                """.strip();
            case "templates/world-setting.md" -> """
                ---
                genre: generic
                ---
                # 世界观设定

                ## 修炼体系


                ## 地理


                ## 势力
                """.strip();
            case "templates/character.md" -> """
                ---
                name: ${name}
                role: protagonist
                status: alive
                ---
                # ${name}

                ## 外貌


                ## 性格


                ## 修为


                ## 背景
                """.strip();
            case "templates/structure.md" -> """
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
                """.strip();
            default -> "";
        };
    }

    /**
     * Base prompt templates for project-local customization.
     * PromptLoader will load from project prompts/ first, then classpath resources.
     */
    private void writePrompt(String name, Path target) throws IOException {
        String content = defaultPrompt("prompts/" + name);
        if (!content.isEmpty()) {
            Files.writeString(target, content, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String defaultPrompt(String name) {
        return switch (name) {
            case "prompts/character-base.md" -> """
                你是一个网络小说的角色设计助手。根据用户的描述，结合已有的项目设定，创建一个角色档案。

                当前项目已有以下设定，请参考以保持世界观一致性：

                ${world_setting}

                已有角色参考：
                ${characters}

                输出格式必须严格遵守以下 Markdown 格式，包含 YAML frontmatter：

                ---
                name: 角色名称
                role: 角色定位（如 protagonist, antagonist, supporting）
                status: 状态（如 alive, dead, unknown）
                ---
                # 角色名称

                ## 外貌


                ## 性格


                ## 修为/能力


                ## 背景


                ## 人际关系

                请直接输出角色档案，不要加其他解释。
                """.strip();
            case "prompts/outline-base.md" -> """
                你是一个网络小说的章节大纲规划助手。根据用户的项目设定和指令，为指定章节编写详细的章节大纲。

                当前项目已有以下设定：

                ${world_setting}

                已有角色：
                ${characters}

                总大纲：
                ${outline}

                输出格式必须严格遵守以下 Markdown 格式，包含 YAML frontmatter：

                ---
                chapter: 章节编号
                title: 章节标题
                word_count_target: 预计字数（如 3000）
                key_characters: [出场角色列表]
                ---
                # 第X章 章节标题

                ## 本章要点
                （2-3句话概括本章核心事件）

                ## 场景一：场景名称
                - 地点：
                - 出场人物：
                - 情节：
                - 伏笔/铺垫：

                ## 场景二：场景名称
                - 地点：
                - 出场人物：
                - 情节：
                - 伏笔/铺垫：

                ## 本章伏笔与悬念


                ## 与前后章节的衔接

                请直接输出章节大纲内容，不要加其他解释。
                """.strip();
            case "prompts/write-base.md" -> """
                你是一个网络小说创作助手。请根据以下设定和大纲，协助用户创作章节内容。

                当前项目已有以下设定：

                ${world_setting}

                已有角色：
                ${characters}

                总大纲：
                ${outline}

                写作要求：
                1. 语言流畅自然，符合网络小说风格
                2. 注意角色性格的一致性
                3. 场景描写要有画面感
                4. 对话要符合角色身份和性格
                5. 注意前后章节的连贯性
                6. 适当设置悬念和伏笔
                7. 每章节字数控制在 3000-5000 字

                请直接输出章节正文内容，使用纯文本格式，不要使用 Markdown 语法（如 #、*、** 等）。
                """.strip();
            default -> "";
        };
    }
}
