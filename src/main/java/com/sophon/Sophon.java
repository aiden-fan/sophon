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
import java.util.ArrayList;
import java.util.List;

/**
 * 应用入口：默认 CLI；{@code --smoke} 冒烟；{@code --web} Spring WebFlux；{@code --batch-tests} 批测。
 */
public final class Sophon {

    private static final Logger log = LoggerFactory.getLogger(Sophon.class);

    private Sophon() {}

    public static void main(String[] args) {
        try {
            LaunchArgs launch = LaunchArgs.parse(args);
            if (launch.isWeb()) {
                SophonWebApplication.main(filterArgsWithout(args, "--web"));
                return;
            }
            AppConfig config = ConfigManager.load();
            if (launch.getSystemPromptFromCli() != null) {
                config.getSophon().getAi().setSystemPrompt(launch.getSystemPromptFromCli());
            }
            if (launch.isBatchTests()) {
                int code = BatchRegressionRunner.run(config);
                System.exit(code);
                return;
            }
            log.debug(
                    "配置加载完成：database.path={}, logging.level={}",
                    config.getSophon().getDatabase().getPath(),
                    config.getSophon().getLogging().getLevel());
            Path dbPath = Path.of(config.getSophon().getDatabase().getPath());
            try (SophonBootstrap.Handle h = SophonBootstrap.launch(config, dbPath)) {
                if (launch.isSmoke()) {
                    runSmoke(h.sessions());
                    return;
                }
                InteractiveChatCli.run(h.chat(), h.sessions(), h.conversation(), h.slashCommands());
            }
        } catch (Exception e) {
            log.error("启动失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    static String[] filterArgsWithout(String[] args, String flag) {
        List<String> out = new ArrayList<>();
        for (String a : args) {
            if (!flag.equals(a)) {
                out.add(a);
            }
        }
        return out.toArray(new String[0]);
    }

    private static void runSmoke(com.sophon.core.session.SessionManager sessions) {
        Session s = sessions.createSession("bootstrap");
        sessions.appendMessage(s.getId(), MessageRole.USER, "hello");
        log.info("Sophon smoke 成功，会话 id={}", s.getId());
    }
}
