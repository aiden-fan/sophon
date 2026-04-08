package com.sophon.server.infrastructure.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${VAR}} placeholders using environment variables and system properties.
 */
final class YamlEnvSubstitutor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::-([^}]*))?}");

    private YamlEnvSubstitutor() {}

    @SuppressWarnings("unchecked")
    static Object substitute(Object node) {
        if (node instanceof String s) {
            return substituteString(s);
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                ((Map<Object, Object>) map).put(e.getKey(), substitute(e.getValue()));
            }
            return map;
        }
        if (node instanceof java.util.List<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (Object o : list) {
                out.add(substitute(o));
            }
            return out;
        }
        return node;
    }

    private static String substituteString(String raw) {
        Matcher m = PLACEHOLDER.matcher(raw);
        if (!m.find()) {
            return raw;
        }
        StringBuffer sb = new StringBuffer();
        m.reset();
        while (m.find()) {
            String key = m.group(1).trim();
            String def = m.group(2);
            String val = firstNonBlank(System.getenv(key), System.getProperty(key), def);
            if (val == null) {
                val = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        String result = sb.toString();
        if (PLACEHOLDER.matcher(result).find()) {
            return substituteString(result);
        }
        return result;
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return null;
    }
}
