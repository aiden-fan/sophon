package com.sophon;

import com.sophon.bootstrap.SophonBootstrap;
import com.sophon.cli.InteractiveChatCli;
import com.sophon.cli.LaunchArgs;
import com.sophon.config.AppConfig;
import com.sophon.config.ConfigManager;
import com.sophon.core.application.BatchRegressionRunner;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import com.sophon.web.SophonWebApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 应用入口：默认仅启动 **Web**（Spring WebFlux）；{@code --cli} 交互终端；{@code --smoke} 写库冒烟；{@code --batch-tests} 批测。
 */
public final class Sophon {

    private static final Logger log = LoggerFactory.getLogger(Sophon.class);

    private Sophon() {}

    public static void main(String[] args) {
        try {
            LaunchArgs launch = LaunchArgs.parse(args);
            if (launch.isCli() && (launch.isSmoke() || launch.isBatchTests())) {
                throw new IllegalArgumentException("--cli 不能与 --smoke / --batch-tests 同时使用");
            }
            if (launch.isBatchTests()) {
                AppConfig config = ConfigManager.load();
                applySystemPromptFromLaunch(launch, config);
                int code = BatchRegressionRunner.run(config);
                System.exit(code);
                return;
            }
            if (launch.isSmoke()) {
                AppConfig config = ConfigManager.load();
                applySystemPromptFromLaunch(launch, config);
                Path dbPath = Path.of(config.getSophon().getDatabase().getPath());
                try (SophonBootstrap.Handle h = SophonBootstrap.launch(config, dbPath)) {
                    runSmoke(h.sessions());
                }
                return;
            }
            if (launch.isCli()) {
                AppConfig config = ConfigManager.load();
                applySystemPromptFromLaunch(launch, config);
                log.debug(
                        "CLI 模式：database.path={}, logging.level={}",
                        config.getSophon().getDatabase().getPath(),
                        config.getSophon().getLogging().getLevel());
                Path dbPath = Path.of(config.getSophon().getDatabase().getPath());
                try (SophonBootstrap.Handle h = SophonBootstrap.launch(config, dbPath)) {
                    InteractiveChatCli.run(
                            h.chat(), h.sessions(), h.conversation(), h.slashCommands(), launch.getResumeSessionId());
                }
                return;
            }
            SophonWebApplication.main(launch.getRemaining());
        } catch (Exception e) {
            log.error("启动失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void applySystemPromptFromLaunch(LaunchArgs launch, AppConfig config) {
        if (launch.getSystemPromptFromCli() != null) {
            config.getSophon().getAi().setSystemPrompt(launch.getSystemPromptFromCli());
        }
    }

    private static void runSmoke(com.sophon.core.session.SessionManager sessions) {
        Session s = sessions.createSession("bootstrap");
        sessions.appendMessage(s.getId(), MessageRole.USER, "hello");
        log.info("Sophon smoke 成功，会话 id={}", s.getId());
    }
}
