package com.sophon.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 按 {@code docs/配置说明.md}「配置覆盖优先级」合并：环境变量优先，其次用户目录
 * {@code ~/.sophon/application.yml}，再 classpath {@code application.yml}，最后代码默认值。
 */
public final class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private ConfigManager() {}

    /**
     * 加载合并后的 {@link AppConfig}。
     *
     * @throws IllegalStateException 当 classpath 中无 {@code application.yml} 且合并结果缺少有效数据库路径时
     * @throws IOException           读取用户配置文件失败时
     */
    public static AppConfig load() throws IOException {
        AppConfig config = AppConfig.defaults();
        boolean mergedClasspath = false;
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (in != null) {
                YAML.readerForUpdating(config).readValue(in);
                mergedClasspath = true;
            }
        }
        Path userYaml = Path.of(System.getProperty("user.home"), ".sophon", "application.yml");
        boolean mergedUser = false;
        if (Files.isRegularFile(userYaml)) {
            YAML.readerForUpdating(config).readValue(userYaml.toFile());
            mergedUser = true;
        }
        normalizePaths(config);
        applyEnvOverrides(config);
        LoggingConfigurator.apply(config);
        if (mergedClasspath) {
            log.debug("已合并 classpath application.yml");
        }
        if (mergedUser) {
            log.debug("已合并用户配置 {}", userYaml.toAbsolutePath());
        }
        String envLogLevel = System.getenv("SOPHON_LOGGING_LEVEL");
        if (envLogLevel != null && !envLogLevel.isBlank()) {
            log.debug("环境变量 SOPHON_LOGGING_LEVEL 覆盖日志级别为 {}", config.getSophon().getLogging().getLevel());
        }
        applyAiEnvOverrides(config);
        validate(config);
        return config;
    }

    private static void normalizePaths(AppConfig config) {
        if (config.getSophon() != null && config.getSophon().getDatabase() != null) {
            String p = config.getSophon().getDatabase().getPath();
            config.getSophon().getDatabase().setPath(ConfigPaths.resolveDataPath(p));
        }
    }

    private static void applyEnvOverrides(AppConfig config) {
        String db = System.getenv("SOPHON_DATABASE_PATH");
        if (db != null && !db.isBlank()) {
            config.getSophon().getDatabase().setPath(ConfigPaths.resolveDataPath(db.trim()));
        }
        String level = System.getenv("SOPHON_LOGGING_LEVEL");
        if (level != null && !level.isBlank()) {
            config.getSophon().getLogging().setLevel(level.trim());
        }
    }

    /** 将 {@code DASHSCOPE_API_KEY} 等写入 {@link AiConfig}；Dashscope 请求侧同样优先读取该环境变量。 */
    private static void applyAiEnvOverrides(AppConfig config) {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key != null && !key.isBlank()) {
            config.getSophon().getAi().setApiKey(key.trim());
        }
        String model = System.getenv("DASHSCOPE_MODEL");
        if (model != null && !model.isBlank()) {
            config.getSophon().getAi().setModel(model.trim());
        }
        String sys = System.getenv("SOPHON_SYSTEM_PROMPT");
        if (sys != null && !sys.isBlank()) {
            config.getSophon().getAi().setSystemPrompt(sys);
        }
    }

    private static void validate(AppConfig config) {
        String path = config.getSophon().getDatabase().getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "sophon.database.path 未设置：请检查 application.yml 或环境变量 SOPHON_DATABASE_PATH");
        }
    }
}
