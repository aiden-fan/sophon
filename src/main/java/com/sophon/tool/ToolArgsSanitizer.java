package com.sophon.tool;

import java.util.Locale;
import java.util.regex.Pattern;

/** 审计与日志用的参数摘要：截断长度并弱化敏感键名（非加密，仅防误打明文密钥）。 */
public final class ToolArgsSanitizer {

    private static final Pattern SENSITIVE_KEY =
            Pattern.compile(
                    "(api[_-]?key|apikey|password|secret|token|authorization|bearer)",
                    Pattern.CASE_INSENSITIVE);

    private ToolArgsSanitizer() {}

    public static String forAudit(String argumentsJson, int maxLen) {
        if (argumentsJson == null) {
            return "";
        }
        String t = argumentsJson.replace('\n', ' ').trim();
        if (t.length() > maxLen) {
            t = t.substring(0, maxLen) + "…(truncated)";
        }
        if (SENSITIVE_KEY.matcher(t).find()) {
            return "[args redacted: possible sensitive keys] len=" + argumentsJson.length();
        }
        return t;
    }

    public static boolean isElevatedTrust(String trustLevel) {
        if (trustLevel == null) {
            return false;
        }
        return "elevated".equalsIgnoreCase(trustLevel.trim());
    }
}
