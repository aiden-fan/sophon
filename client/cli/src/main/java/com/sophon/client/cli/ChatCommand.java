package com.sophon.client.cli;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "chat", description = "POST /api/v1/chat")
public class ChatCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-u", "--url"}, description = "Server base URL")
    String baseUrl;

    @CommandLine.Option(names = {"-t", "--token"}, description = "Bearer token")
    String token;

    @CommandLine.Option(names = {"-s", "--session"}, description = "Session id or name (default: main)")
    String sessionId;

    @CommandLine.Option(names = {"--stream"}, description = "Use streaming output")
    boolean stream;

    @CommandLine.Option(names = {"--rag-top-k"}, description = "Override RAG topK")
    Integer ragTopK;

    @CommandLine.Option(names = {"--rag-min-score"}, description = "Override RAG min score")
    Double ragMinScore;

    @CommandLine.Parameters(paramLabel = "MESSAGE", description = "User message")
    String message;

    @Override
    public Integer call() throws Exception {
        ChatClient.run(baseUrl, token, sessionId, message, stream, ragTopK, ragMinScore);
        return 0;
    }
}
