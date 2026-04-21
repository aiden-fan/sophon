package com.sophon.skill;

import com.sophon.skill.model.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLoaderTest {

    @Test
    void loadSkillMd_derivesIdFromHyphenatedName() throws Exception {
        SkillLoader loader = new SkillLoader();
        SkillDefinition def =
                loader.loadSkillMd(
                        """
                        ---
                        name: my-skill
                        title: My Skill
                        description: Does something.
                        requiredTools:
                          - echo
                        ---
                        # Body
                        """);
        assertEquals("my_skill", def.getId());
        assertEquals("My Skill", def.getName());
        assertEquals(List.of("echo"), def.getRequiredTools());
    }

    @Test
    void loadSkillMd_explicitIdOverridesName() throws Exception {
        SkillLoader loader = new SkillLoader();
        SkillDefinition def =
                loader.loadSkillMd(
                        """
                        ---
                        name: display-only
                        id: custom_id
                        description: Test.
                        ---
                        """);
        assertEquals("custom_id", def.getId());
    }

    @Test
    void loadSkillMd_requiresFrontmatter() {
        SkillLoader loader = new SkillLoader();
        assertThrows(IllegalArgumentException.class, () -> loader.loadSkillMd("# No frontmatter\n"));
    }

    @Test
    void normalizeSkillId_hyphensToUnderscores() {
        assertEquals("markdown_doc", SkillLoader.normalizeSkillId("markdown-doc"));
        assertEquals("greet", SkillLoader.normalizeSkillId("greet"));
    }

    @Test
    void loadSkillMdResource_builtinMarkdownDoc() throws Exception {
        SkillLoader loader = new SkillLoader();
        SkillDefinition def =
                loader.loadSkillMdResource(
                        getClass().getClassLoader(), "skills/markdown_doc/SKILL.md");
        assertEquals("markdown_doc", def.getId());
        assertTrue(def.getDescription().contains("write_file"));
        assertTrue(def.getRequiredTools().contains("read_file"));
        assertTrue(def.getRequiredTools().contains("create_file"));
        assertTrue(def.getRequiredTools().contains("write_file"));
    }

    @Test
    void loadSkillMdResource_builtinNovelWriter() throws Exception {
        SkillLoader loader = new SkillLoader();
        SkillDefinition def =
                loader.loadSkillMdResource(
                        getClass().getClassLoader(), "skills/novel_writer/SKILL.md");
        assertEquals("novel_writer", def.getId());
        assertTrue(def.getDescription().contains("网络小说"));
        assertTrue(def.getRequiredTools().contains("build_novel_prompt"));
    }
}
