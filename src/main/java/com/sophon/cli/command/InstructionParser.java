package com.sophon.cli.command;

import com.sophon.core.tool.NovelProjectPath;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstructionParser {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第?(\\d+)章");

    private InstructionParser() {
    }

    public static int extractChapter(String instruction) {
        Matcher m = CHAPTER_PATTERN.matcher(instruction);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = Pattern.compile("(\\d+)").matcher(instruction);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 1;
    }

    public static String extractChapterTitle(String instruction, NovelProjectPath projectPath) {
        String cleaned = instruction.replaceAll("写第\\d+章\\s*", "")
            .replaceAll("第\\d+章\\s*", "")
            .replaceAll("写", "")
            .replaceAll("续写", "")
            .replaceAll("创作", "")
            .trim();
        if (cleaned.isBlank()) return "未命名";
        String shortened = cleaned.length() > 15 ? cleaned.substring(0, 15) : cleaned;
        return projectPath.sanitizeFileName(shortened, "未命名");
    }

    public static String extractCharacterName(String content, String description) {
        int start = content.indexOf("name:");
        if (start >= 0) {
            int end = content.indexOf('\n', start);
            if (end > 0) {
                String nameLine = content.substring(start + 5, end).trim();
                if (!nameLine.isBlank()) return nameLine;
            }
        }
        int heading = content.indexOf("# ");
        if (heading >= 0) {
            int headingEnd = content.indexOf('\n', heading);
            if (headingEnd > 0) {
                String headingText = content.substring(heading + 2, headingEnd).trim();
                if (!headingText.isBlank()) return headingText;
            }
        }
        String[] parts = description.trim().split("[\\s,，、]+");
        return parts.length == 0 ? "未命名角色" : parts[0];
    }
}
