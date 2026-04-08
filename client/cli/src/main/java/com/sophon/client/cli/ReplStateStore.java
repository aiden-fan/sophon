package com.sophon.client.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ReplStateStore {

    private static final String FILE_NAME = "cli-repl-state.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ReplStateStore() {}

    static Path statePath() {
        String home = System.getenv("SOPHON_HOME");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.home") + "/.sophon";
        }
        return Path.of(home, "config", FILE_NAME);
    }

    static ReplState loadOrDefaults() {
        Path path = statePath();
        if (!Files.isRegularFile(path)) {
            return ReplState.defaults();
        }
        try {
            return MAPPER.readValue(path.toFile(), ReplState.class);
        } catch (IOException e) {
            return ReplState.defaults();
        }
    }

    static void save(ReplState state) {
        Path path = statePath();
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), state);
        } catch (IOException ignored) {
        }
    }
}
