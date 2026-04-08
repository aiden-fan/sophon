package com.sophon.server.infrastructure.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code server.yaml} and applies env substitution.
 */
public final class ServerYamlLoader {

    private ServerYamlLoader() {}

    public static Map<String, Object> load(Path serverYamlPath) throws IOException {
        if (!Files.isRegularFile(serverYamlPath)) {
            return new LinkedHashMap<>();
        }
        String text = Files.readString(serverYamlPath, StandardCharsets.UTF_8);
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(text);
        if (parsed == null) {
            return new LinkedHashMap<>();
        }
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IOException("server.yaml root must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        Object substituted = YamlEnvSubstitutor.substitute(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) substituted;
        return out;
    }
}
