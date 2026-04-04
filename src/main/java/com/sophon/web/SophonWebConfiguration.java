package com.sophon.web;

import com.sophon.bootstrap.SophonBootstrap;
import com.sophon.config.AppConfig;
import com.sophon.config.ConfigManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class SophonWebConfiguration {

    @Bean
    public AppConfig webAppConfig() throws IOException {
        return ConfigManager.load();
    }

    @Bean(destroyMethod = "close")
    public SophonBootstrap.Handle sophonWebHandle(AppConfig cfg) throws Exception {
        Path dbPath = Path.of(cfg.getSophon().getDatabase().getPath());
        return SophonBootstrap.launch(cfg, dbPath);
    }

    @Bean
    public RouterFunction<ServerResponse> sophonWebRoutes(SophonBootstrap.Handle h) {
        return SophonWebRouter.build(h);
    }
}
