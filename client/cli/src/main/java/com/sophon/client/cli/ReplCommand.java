package com.sophon.client.cli;

import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "repl", description = "Interactive chat REPL")
public class ReplCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-u", "--url"}, description = "Server base URL (overrides saved)")
    String baseUrl;

    @CommandLine.Option(names = {"-t", "--token"}, description = "Bearer token")
    String token;

    @CommandLine.Option(names = {"-s", "--session"}, description = "Session id or name (overrides saved; default main)")
    String sessionId;

    @CommandLine.Option(
            names = {"--stream-mode"},
            defaultValue = "auto",
            description = "Streaming: auto (use saved), on, off"
    )
    String streamMode;

    @CommandLine.Option(names = {"--rag-top-k"}, description = "RAG topK (overrides saved)")
    Integer ragTopK;

    @CommandLine.Option(names = {"--rag-min-score"}, description = "RAG min score (overrides saved)")
    Double ragMinScore;

    @Override
    public Integer call() throws Exception {
        ReplState saved = ReplStateStore.loadOrDefaults();

        String currentSession = firstNonBlank(sessionId, saved.session(), "main");
        String currentBaseUrl = firstNonBlank(baseUrl, saved.baseUrl());
        boolean currentStream = resolveStreamMode(streamMode, saved.stream());
        Integer currentTopK = ragTopK != null ? ragTopK : saved.ragTopK();
        Double currentMinScore = ragMinScore != null ? ragMinScore : saved.ragMinScore();

        System.out.println("进入 REPL。输入 /help 查看命令，/exit 退出。");
        System.out.println("状态文件: " + ReplStateStore.statePath());
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (true) {
                System.out.print("sophon(" + currentSession + ")> ");
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String input = line.trim();
                if (input.isEmpty()) {
                    continue;
                }
                if ("/exit".equalsIgnoreCase(input) || "/quit".equalsIgnoreCase(input)) {
                    break;
                }
                if ("/help".equalsIgnoreCase(input)) {
                    printHelp();
                    continue;
                }
                if (input.startsWith("/session ")) {
                    String v = input.substring("/session ".length()).trim();
                    if (!v.isBlank()) {
                        currentSession = v;
                        System.out.println("已切换会话: " + currentSession);
                        persist(currentSession, currentStream, currentTopK, currentMinScore, currentBaseUrl);
                    }
                    continue;
                }
                if (input.startsWith("/stream ")) {
                    String v = input.substring("/stream ".length()).trim().toLowerCase();
                    currentStream = "on".equals(v) || "true".equals(v) || "1".equals(v);
                    System.out.println("stream=" + currentStream);
                    persist(currentSession, currentStream, currentTopK, currentMinScore, currentBaseUrl);
                    continue;
                }
                if (input.startsWith("/rag ")) {
                    String[] parts = input.split("\\s+");
                    if (parts.length >= 2) {
                        currentTopK = Integer.parseInt(parts[1]);
                    }
                    if (parts.length >= 3) {
                        currentMinScore = Double.parseDouble(parts[2]);
                    }
                    System.out.println("ragTopK=" + currentTopK + ", ragMinScore=" + currentMinScore);
                    persist(currentSession, currentStream, currentTopK, currentMinScore, currentBaseUrl);
                    continue;
                }

                try {
                    ChatClient.run(currentBaseUrl, token, currentSession, input, currentStream, currentTopK, currentMinScore);
                } catch (Exception ex) {
                    System.out.println("请求失败: " + ex.getMessage());
                }
            }
        } finally {
            persist(currentSession, currentStream, currentTopK, currentMinScore, currentBaseUrl);
        }
        return 0;
    }

    private static void persist(
            String session,
            boolean stream,
            Integer ragTopK,
            Double ragMinScore,
            String baseUrl
    ) {
        ReplStateStore.save(new ReplState(session, stream, ragTopK, ragMinScore, blankToNull(baseUrl)));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return fallback;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static boolean resolveStreamMode(String mode, boolean saved) {
        if (mode == null || mode.isBlank()) {
            return saved;
        }
        return switch (mode.trim().toLowerCase()) {
            case "on", "true", "1" -> true;
            case "off", "false", "0" -> false;
            case "auto" -> saved;
            default -> saved;
        };
    }

    private static void printHelp() {
        System.out.println("/help                 显示帮助");
        System.out.println("/exit | /quit         退出 REPL（会保存会话与配置）");
        System.out.println("/session <id|name>    切换会话");
        System.out.println("/stream on|off        开关流式");
        System.out.println("/rag <topK> <min>     设置 RAG 参数");
        System.out.println("其他任意文本将作为消息发送");
    }
}
