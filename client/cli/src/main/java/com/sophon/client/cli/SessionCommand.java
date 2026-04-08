package com.sophon.client.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophon.common.dto.SessionResponse;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "session",
        description = "Session management (in-memory skeleton)",
        subcommands = {
                SessionCommand.Create.class,
                SessionCommand.ListSessions.class
        }
)
public class SessionCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    @CommandLine.Command(name = "create", description = "POST /api/v1/sessions")
    static class Create implements Callable<Integer> {

        @CommandLine.Option(names = {"-u", "--url"}, description = "Server base URL")
        String baseUrl;

        @CommandLine.Option(names = {"-t", "--token"})
        String token;

        @CommandLine.Option(names = {"-n", "--name"}, defaultValue = "CLI session")
        String name;

        @Override
        public Integer call() throws Exception {
            String root = HealthCommand.resolveBaseUrl(baseUrl);
            String url = root.replaceAll("/+$", "") + "/api/v1/sessions";
            String body = MAPPER.writeValueAsString(java.util.Map.of("name", name));
            String json = HttpSupport.postJson(url, body, token);
            SessionResponse s = MAPPER.readValue(json, SessionResponse.class);
            System.out.println("id=" + s.id() + " name=" + s.name() + " createdAt=" + s.createdAt());
            return 0;
        }
    }

    @CommandLine.Command(name = "list", description = "GET /api/v1/sessions")
    static class ListSessions implements Callable<Integer> {

        @CommandLine.Option(names = {"-u", "--url"}, description = "Server base URL")
        String baseUrl;

        @CommandLine.Option(names = {"-t", "--token"})
        String token;

        @Override
        public Integer call() throws Exception {
            String root = HealthCommand.resolveBaseUrl(baseUrl);
            String url = root.replaceAll("/+$", "") + "/api/v1/sessions";
            String json = HttpSupport.getJson(url, token);
            List<SessionResponse> list = MAPPER.readValue(json, new TypeReference<>() {});
            for (SessionResponse s : list) {
                System.out.println(s.id() + "\t" + s.name() + "\t" + s.createdAt());
            }
            return 0;
        }
    }
}
