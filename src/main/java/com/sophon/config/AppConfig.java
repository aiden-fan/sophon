package com.sophon.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public record AppConfig(AiConfig ai) {
    public static AppConfig load() {
        Map<String, Object> root = Map.of();
        try (InputStream is = AppConfig.class.getResourceAsStream("/application.yml")) {
            if (is != null) {
                Object loaded = new Yaml().load(is);
                if (loaded instanceof Map<?, ?> map) {
                    root = (Map<String, Object>) map;
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> sophon = getMap(root, "sophon");
        Map<String, Object> aiMap = getMap(sophon, "ai");
        Map<String, Object> dashscopeMap = getMap(aiMap, "dashscope");

        String provider = resolveEnvLike(stringValue(aiMap.get("provider"), "mock"));
        String model = resolveEnvLike(stringValue(dashscopeMap.get("model"), "qwen-plus"));
        String apiKeyFromYaml = resolveEnvLike(stringValue(dashscopeMap.get("api-key"), ""));
        String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", apiKeyFromYaml);

        return new AppConfig(new AiConfig(provider, apiKey, model));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return Map.of();
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? defaultValue : str;
    }

    private static String resolveEnvLike(String raw) {
        if (raw == null) return "";
        if (!raw.startsWith("${") || !raw.endsWith("}")) return raw;
        String expr = raw.substring(2, raw.length() - 1);
        String[] parts = expr.split(":", 2);
        String envName = parts[0];
        String fallback = parts.length > 1 ? parts[1] : "";
        return System.getenv().getOrDefault(envName, fallback);
    }
}
