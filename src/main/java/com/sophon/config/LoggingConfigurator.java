package com.sophon.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

/**
 * 将 {@link AppConfig} 中的 {@code sophon.logging.level} 应用到 Logback（包 {@code com.sophon}）。
 * 须在首次使用业务 Logger 之前调用（通常在 {@code ConfigManager.load()} 之后、其余初始化之前）。
 */
public final class LoggingConfigurator {

    private LoggingConfigurator() {}

    public static void apply(AppConfig config) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger sophon = lc.getLogger("com.sophon");
        String raw = config.getSophon().getLogging().getLevel();
        if (raw == null || raw.isBlank()) {
            sophon.setLevel(Level.INFO);
            return;
        }
        Level level = Level.toLevel(raw.trim(), Level.INFO);
        sophon.setLevel(level);
    }
}
