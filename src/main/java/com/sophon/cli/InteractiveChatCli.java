package com.sophon.cli;

import com.sophon.ai.dto.ModelOutputKind;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.core.application.ChatApplicationService;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.MessageDisplayFormatter;
import com.sophon.core.session.SessionManager;
import com.sophon.i18n.CliMessages;
import com.sophon.model.Message;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * CLI：优先 JLine（阶段 17）；支持 {@code SOPHON_REGENERATE} 自动重发用户话（阶段 15）。
 */
public final class InteractiveChatCli {

    private static final Logger log = LoggerFactory.getLogger(InteractiveChatCli.class);
    private static final String REGENERATE_PREFIX = "SOPHON_REGENERATE:\n";

    private InteractiveChatCli() {}

    public static void run(
            ChatApplicationService chat,
            SessionManager sessions,
            ConversationManager conversation,
            SlashCommandRouter slashCommands,
            String resumeSessionIdOrNull) {
        Session s;
        if (resumeSessionIdOrNull != null && !resumeSessionIdOrNull.isBlank()) {
            String id = resumeSessionIdOrNull.trim();
            s =
                    sessions.getSession(id)
                            .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + id));
            log.info("恢复会话 id={}，输入 exit 或 quit 结束", s.getId());
            System.out.println(CliMessages.get("cli.banner.resume") + " " + s.getId());
        } else {
            s = sessions.createSession("cli");
            log.info("交互会话已创建 id={}，输入 exit 或 quit 结束", s.getId());
            System.out.println(CliMessages.get("cli.banner.session") + " " + s.getId());
        }
        System.out.println(CliMessages.get("cli.banner.hint"));
        System.out.println(CliMessages.get("cli.banner.slash"));
        printTranscriptIfAny(sessions, s.getId());

        try {
            Session[] current = new Session[] {s};
            runLoop(chat, slashCommands, sessions, current);
        } catch (Exception e) {
            log.error("CLI 读取失败", e);
            System.err.println("输入异常: " + e.getMessage());
        }
    }

    private static void runLoop(ChatApplicationService chat, SlashCommandRouter slashCommands, SessionManager sessions, Session[] current)
            throws Exception {
        LineReader jlineReader = tryCreateJLineReader();
        BufferedReader fallback =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            String line;
            if (jlineReader != null) {
                try {
                    line = jlineReader.readLine("sophon> ");
                } catch (Exception e) {
                    log.debug("JLine 读行失败，回退标准输入: {}", e.getMessage());
                    jlineReader = null;
                    line = fallback.readLine();
                }
            } else {
                line = fallback.readLine();
            }
            if (line == null) {
                break;
            }
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if ("exit".equalsIgnoreCase(t) || "quit".equalsIgnoreCase(t)) {
                break;
            }
            var slashOut = slashCommands.route(t, current[0].getId());
            if (slashOut.isPresent()) {
                String out = slashOut.get();
                if (out.startsWith(SlashCommandRouter.OUTPUT_SESSION_SWITCH_PREFIX)) {
                    String newId =
                            out.substring(SlashCommandRouter.OUTPUT_SESSION_SWITCH_PREFIX.length()).trim();
                    Session ns =
                            sessions.getSession(newId)
                                    .orElseThrow(() -> new IllegalStateException("会话不存在: " + newId));
                    current[0] = ns;
                    System.out.println(CliMessages.get("cli.session.switched") + ns.getId());
                    printTranscriptIfAny(sessions, ns.getId());
                    continue;
                }
                if (out.startsWith(REGENERATE_PREFIX)) {
                    String userAgain = out.substring(REGENERATE_PREFIX.length());
                    System.out.println("[重答] 正在重新生成…");
                    runChatStream(chat, current[0].getId(), userAgain);
                    continue;
                }
                System.out.println(out);
                continue;
            }
            runChatStream(chat, current[0].getId(), t);
        }
        if (jlineReader != null && jlineReader.getTerminal() != null) {
            try {
                jlineReader.getTerminal().close();
            } catch (Exception ignored) {
            }
        }
    }

    private static LineReader tryCreateJLineReader() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build();
            return LineReaderBuilder.builder().terminal(terminal).build();
        } catch (Exception e) {
            log.debug("JLine 不可用，使用标准输入: {}", e.getMessage());
            return null;
        }
    }

    private static void runChatStream(ChatApplicationService chat, String sessionId, String text) {
        try {
            chat.chatStream(sessionId, text)
                    .doOnNext(InteractiveChatCli::printStreamChunk)
                    .doOnComplete(() -> System.out.println())
                    .blockLast(Duration.ofMinutes(30));
        } catch (IllegalArgumentException e) {
            System.err.println("[错误] " + e.getMessage());
        } catch (Exception e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            System.err.println("[模型错误] " + c.getMessage());
            log.warn("模型流式调用失败", e);
        }
    }

    private static void printTranscriptIfAny(SessionManager sessions, String sessionId) {
        List<Message> rows = sessions.listMessages(sessionId);
        if (rows.isEmpty()) {
            return;
        }
        System.out.println(CliMessages.get("cli.history.start"));
        for (Message m : rows) {
            String role = messageRoleLabel(m.getRole());
            String body = MessageDisplayFormatter.formatContent(m);
            System.out.println(role + " " + indentBody(body));
            System.out.println();
        }
        System.out.println(CliMessages.get("cli.history.end"));
    }

    private static String messageRoleLabel(MessageRole r) {
        if (r == null) {
            return CliMessages.get("cli.msg.user");
        }
        return switch (r) {
            case USER -> CliMessages.get("cli.msg.user");
            case ASSISTANT -> CliMessages.get("cli.msg.assistant");
            case TOOL -> CliMessages.get("cli.msg.tool");
            case SYSTEM -> CliMessages.get("cli.msg.system");
        };
    }

    private static String indentBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        return body.replace("\n", "\n    ");
    }

    private static void printStreamChunk(StreamingChunk chunk) {
        if (chunk instanceof StreamingChunk.TextToken tt) {
            if (tt.kind() == ModelOutputKind.THINKING) {
                System.out.print("\u001b[36m");
                System.out.print(tt.text());
                System.out.print("\u001b[0m");
            } else {
                System.out.print(tt.text());
            }
            return;
        }
        if (chunk instanceof StreamingChunk.Progress p) {
            System.out.print("\n[进度] " + p.detail() + "\n");
        }
    }
}
