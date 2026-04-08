package com.sophon.client.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophon.common.dto.HealthResponse;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "health", description = "Call GET /api/v1/health")
public class HealthCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @CommandLine.Option(names = {"-u", "--url"}, description = "Server base URL (overrides SOPHON_SERVER_URL)")
    String baseUrl;

    @CommandLine.Option(names = {"-t", "--token"}, description = "Bearer token (if server auth is enabled)")
    String token;

    @Override
    public Integer call() throws Exception {
        String root = resolveBaseUrl(baseUrl);
        String url = root.replaceAll("/+$", "") + "/api/v1/health";
        String json = HttpSupport.getJson(url, token);
        HealthResponse h = MAPPER.readValue(json, HealthResponse.class);
        System.out.println("status=" + h.status() + " version=" + h.version() + " revision=" + h.configRevision());
        return 0;
    }

    static String resolveBaseUrl(String optionUrl) {
        if (optionUrl != null && !optionUrl.isBlank()) {
            return optionUrl.trim();
        }
        String env = System.getenv("SOPHON_SERVER_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return "http://127.0.0.1:8080";
    }
}
