package com.sophon.cli;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class CliApp {
    private final Terminal terminal;
    private final LineReader reader;
    private final CommandRouter router;

    public CliApp() {
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
            this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
            this.router = new CommandRouter(terminal);
        } catch (Exception e) {
            throw new RuntimeException("终端初始化失败", e);
        }
    }

    public void run() {
        printBanner();
        terminal.writer().println();
        terminal.writer().println("输入 /help 查看可用命令");
        terminal.writer().println();
        terminal.writer().flush();

        while (true) {
            String line;
            try {
                line = reader.readLine("sophon> ");
            } catch (UserInterruptException e) {
                terminal.writer().println("\n按 Ctrl+D 退出，或继续输入");
                continue;
            } catch (EndOfFileException e) {
                terminal.writer().println("\n再见！");
                try { terminal.close(); } catch (Exception ignored) {}
                return;
            }

            if (line.isBlank()) continue;

            if (line.startsWith("/")) {
                String cmd = line.split("\\s+")[0].substring(1);
                if ("quit".equals(cmd) || "exit".equals(cmd)) {
                    terminal.writer().println("\n再见！");
                    try { terminal.close(); } catch (Exception ignored) {}
                    return;
                }
                handleSlashCommand(line);
            } else {
                handleChat(line);
            }

            terminal.writer().flush();
        }
    }

    private void handleSlashCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].substring(1);
        String args = parts.length > 1 ? parts[1] : "";
        router.execute(command, args);
    }

    private void handleChat(String message) {
        terminal.writer().println();
        terminal.writer().print("[AI] ");
        router.chat(message);
        terminal.writer().println();
        terminal.writer().println();
    }

    private void printBanner() {
        String banner = """
             _____       __
            / ___/____  / /_
            \\__ \\/ __ \\/ __/
           ___/ / /_/ / /_
          /____/ .___/\\__/
              /_/
            小说创作助手
            """;
        terminal.writer().println(banner);
    }
}
