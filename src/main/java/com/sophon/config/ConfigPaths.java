package com.sophon.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置中的路径解析（{@code ~}、{@code ${user.home}}）。
 */
final class ConfigPaths {

    private ConfigPaths() {}

    static String resolveDataPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return Path.of(System.getProperty("user.home"), ".sophon", "data", "sophon.db").toString();
        }
        String s = raw.trim();
        if (s.startsWith("~")) {
            s = System.getProperty("user.home") + s.substring(1);
        }
        s = s.replace("${user.home}", System.getProperty("user.home"));
        return s;
    }
}
