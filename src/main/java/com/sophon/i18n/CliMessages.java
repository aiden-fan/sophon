package com.sophon.i18n;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** 阶段 18：CLI 文案；环境变量 {@code SOPHON_LANG=zh} 时优先中文。 */
public final class CliMessages {

    private CliMessages() {}

    public static ResourceBundle bundle() {
        String env = System.getenv("SOPHON_LANG");
        Locale loc =
                env != null && env.toLowerCase(Locale.ROOT).startsWith("zh")
                        ? Locale.SIMPLIFIED_CHINESE
                        : Locale.ROOT;
        return ResourceBundle.getBundle("com.sophon.i18n.messages", loc);
    }

    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
