package com.sophon.server.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies {@code server.yaml} host/port to Spring Boot before the web server starts.
 */
public class SophonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "sophon-server-yaml-bootstrap";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String home = environment.getProperty("sophon.home");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.home") + "/.sophon";
        }
        ensureHomeLayout(home);
        Path path = Path.of(home, "config", "server.yaml");
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Map<String, Object> root = ServerYamlLoader.load(path);
            SophonRuntimeConfig cfg = SophonRuntimeConfig.fromRootMap(root);
            Map<String, Object> props = new LinkedHashMap<>();
            Integer port = cfg.getServer().getPort();
            if (port != null) {
                props.put("server.port", port);
            }
            String host = cfg.getServer().getHost();
            if (host != null && !host.isBlank()) {
                props.put("server.address", host);
            }
            if (!props.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static void ensureHomeLayout(String home) {
        try {
            Files.createDirectories(Path.of(home, "config"));
            Files.createDirectories(Path.of(home, "data"));
            Files.createDirectories(Path.of(home, "data", "database"));
            Files.createDirectories(Path.of(home, "data", "cache"));
            Files.createDirectories(Path.of(home, "logs"));
            Files.createDirectories(Path.of(home, "plugins"));
            Files.createDirectories(Path.of(home, "sessions"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize sophon home: " + home, e);
        }
    }
}
