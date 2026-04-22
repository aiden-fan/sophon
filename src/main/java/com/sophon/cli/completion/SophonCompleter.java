package com.sophon.cli.completion;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 斜杠命令与子命令补全；{@code /novel open} 之后为目录名补全（含检测到含 {@code outline.md} 的小说项目目录）。
 */
public final class SophonCompleter implements Completer {

    private static final Set<String> ROOT_COMMANDS = Set.of(
        "help", "quit", "exit", "novel", "new", "list", "info",
        "write", "w", "character", "c", "outline", "o", "chapter"
    );

    private static final Set<String> NOVEL_SUB = Set.of("open", "info");

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (!line.line().startsWith("/")) {
            return;
        }

        List<String> words = line.words();
        int wi = line.wordIndex();
        if (words.isEmpty()) {
            return;
        }

        if (wi == 0) {
            completeRootCommand(words.get(0), candidates);
            return;
        }

        String w0 = words.get(0);
        if (!"/novel".equals(w0)) {
            return;
        }

        if (wi == 1) {
            String partial = line.word() == null ? "" : line.word().toLowerCase(Locale.ROOT);
            for (String sub : NOVEL_SUB) {
                if (sub.startsWith(partial)) {
                    candidates.add(new Candidate(sub + " ", sub, null, null, null, null, true));
                }
            }
            return;
        }

        if (wi >= 2 && words.size() >= 2 && "open".equalsIgnoreCase(words.get(1))) {
            String pathWord = line.word() == null ? "" : line.word();
            completePath(pathWord, candidates);
        }
    }

    private static void completeRootCommand(String firstWord, List<Candidate> candidates) {
        if (!firstWord.startsWith("/")) {
            return;
        }
        String prefix = firstWord.substring(1).toLowerCase(Locale.ROOT);
        for (String cmd : ROOT_COMMANDS) {
            if (cmd.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                candidates.add(new Candidate("/" + cmd + " ", cmd, null, null, null, null, true));
            }
        }
    }

    private static void completePath(String pathWord, List<Candidate> candidates) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path userHome = Path.of(System.getProperty("user.home", "."));

        String raw = pathWord == null ? "" : pathWord;
        Path base = cwd;
        String logical = raw;
        if (raw.startsWith("~/")) {
            base = userHome;
            logical = raw.substring(2);
        } else if (raw.startsWith("~") && raw.length() > 1 && raw.charAt(1) != '/') {
            base = userHome;
            logical = raw.substring(1);
        }

        Path dir;
        String parentLogical;
        String namePrefix;

        if (logical.isEmpty()) {
            dir = base;
            parentLogical = "";
            namePrefix = "";
        } else if (logical.endsWith("/")) {
            dir = base.resolve(logical).normalize();
            if (!Files.isDirectory(dir)) {
                return;
            }
            parentLogical = logical;
            namePrefix = "";
        } else {
            Path resolved = base.resolve(logical).normalize();
            if (Files.isDirectory(resolved)) {
                dir = resolved;
                parentLogical = logical + "/";
                namePrefix = "";
            } else {
                int lastSlash = logical.lastIndexOf('/');
                if (lastSlash < 0) {
                    dir = base;
                    parentLogical = "";
                    namePrefix = logical;
                } else {
                    parentLogical = logical.substring(0, lastSlash + 1);
                    namePrefix = logical.substring(lastSlash + 1);
                    dir = base.resolve(parentLogical).normalize();
                }
                if (!Files.isDirectory(dir)) {
                    return;
                }
            }
        }

        List<PathEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith(".") && namePrefix.isEmpty() && parentLogical.isEmpty()) {
                    return;
                }
                if (!namePrefix.isEmpty() && !name.startsWith(namePrefix)) {
                    return;
                }
                boolean isDir = Files.isDirectory(p);
                boolean novelProject = isDir && Files.isRegularFile(p.resolve("outline.md"));
                entries.add(new PathEntry(p, name, isDir, novelProject));
            });
        } catch (IOException ignored) {
            return;
        }

        entries.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        for (PathEntry e : entries) {
            String suffix = e.isDir ? "/" : " ";
            String logicalValue = parentLogical + e.name + suffix;
            String value = toOpenArgument(raw, logicalValue);
            String desc = e.novelProject ? "小说项目" : (e.isDir ? "目录" : "文件");
            candidates.add(new Candidate(value, value, null, desc, null, null, true));
        }
    }

    /** 补全结果与当前输入的前缀风格一致（如保留 {@code ~/}）。 */
    private static String toOpenArgument(String rawToken, String logicalValue) {
        if (rawToken.startsWith("~/")) {
            return "~/" + logicalValue;
        }
        if (rawToken.startsWith("~") && rawToken.length() > 1 && rawToken.charAt(1) != '/') {
            return "~" + logicalValue;
        }
        return logicalValue;
    }

    private record PathEntry(Path path, String name, boolean isDir, boolean novelProject) {
    }
}
