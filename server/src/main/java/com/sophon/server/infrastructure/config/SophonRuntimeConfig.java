package com.sophon.server.infrastructure.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed view of effective {@code server.yaml} (after env substitution).
 */
public final class SophonRuntimeConfig {

    private final ServerSection server;
    private final AuthenticationSection authentication;
    private final Map<String, Object> raw;

    public SophonRuntimeConfig(ServerSection server, AuthenticationSection authentication, Map<String, Object> raw) {
        this.server = Objects.requireNonNullElse(server, new ServerSection(null, null, null, null));
        this.authentication = Objects.requireNonNullElse(authentication, new AuthenticationSection(false, null, List.of()));
        this.raw = raw == null ? Map.of() : Collections.unmodifiableMap(raw);
    }

    public ServerSection getServer() {
        return server;
    }

    public AuthenticationSection getAuthentication() {
        return authentication;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public static final class ServerSection {
        private final String host;
        private final Integer port;
        private final String protocol;
        private final Integer maxConnections;

        public ServerSection(String host, Integer port, String protocol, Integer maxConnections) {
            this.host = host;
            this.port = port;
            this.protocol = protocol;
            this.maxConnections = maxConnections;
        }

        public String getHost() {
            return host;
        }

        public Integer getPort() {
            return port;
        }

        public String getProtocol() {
            return protocol;
        }

        public Integer getMaxConnections() {
            return maxConnections;
        }
    }

    public static final class AuthenticationSection {
        private final boolean enabled;
        private final String token;
        private final List<String> allowedClients;

        public AuthenticationSection(boolean enabled, String token, List<String> allowedClients) {
            this.enabled = enabled;
            this.token = token;
            this.allowedClients = allowedClients == null ? List.of() : List.copyOf(allowedClients);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getToken() {
            return token;
        }

        public List<String> getAllowedClients() {
            return allowedClients;
        }
    }

    public static SophonRuntimeConfig fromRootMap(Map<String, Object> root) {
        Map<String, Object> serverMap = getMap(root, "server");
        String host = getString(serverMap, "host");
        Integer port = getInt(serverMap, "port");
        String protocol = getString(serverMap, "protocol");
        Integer maxConnections = getInt(serverMap, "max_connections");

        Map<String, Object> authMap = getMap(root, "authentication");
        boolean authEnabled = getBoolean(authMap, "enabled", false);
        String token = getString(authMap, "token");
        @SuppressWarnings("unchecked")
        List<String> allowed = authMap != null && authMap.get("allowed_clients") instanceof List<?> l
                ? l.stream().map(Object::toString).toList()
                : List.of();

        return new SophonRuntimeConfig(
                new ServerSection(host, port, protocol, maxConnections),
                new AuthenticationSection(authEnabled, token, allowed),
                root
        );
    }

    private static Map<String, Object> getMap(Map<String, Object> root, String key) {
        if (root == null) {
            return null;
        }
        Object v = root.get(key);
        if (v instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            return cast;
        }
        return null;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object v = map.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(v.toString().trim());
    }
}
