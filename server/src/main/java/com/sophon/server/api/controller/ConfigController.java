package com.sophon.server.api.controller;

import com.sophon.common.dto.ConfigInfoResponse;
import com.sophon.server.infrastructure.config.LoadedServerConfig;
import com.sophon.server.infrastructure.config.SophonProperties;
import com.sophon.server.infrastructure.config.SophonRuntimeConfig;
import com.sophon.server.infrastructure.config.SophonRuntimeConfigHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final SophonRuntimeConfigHolder holder;
    private final SophonProperties sophonProperties;

    @Value("${server.port}")
    private int serverPort;

    @Value("${server.address:0.0.0.0}")
    private String serverAddress;

    public ConfigController(SophonRuntimeConfigHolder holder, SophonProperties sophonProperties) {
        this.holder = holder;
        this.sophonProperties = sophonProperties;
    }

    @GetMapping
    public Mono<ConfigInfoResponse> snapshot() {
        LoadedServerConfig loaded = holder.current();
        SophonRuntimeConfig cfg = loaded.config();
        return Mono.just(new ConfigInfoResponse(
                loaded.revision(),
                sophonProperties.getHome(),
                serverAddress,
                serverPort,
                cfg.getAuthentication().isEnabled(),
                loaded.sourcePath().toString(),
                loaded.loadedAtEpochMs()
        ));
    }

    @PostMapping("/reload")
    public Mono<ConfigInfoResponse> reload() {
        LoadedServerConfig loaded = holder.reloadFromDisk();
        SophonRuntimeConfig cfg = loaded.config();
        return Mono.just(new ConfigInfoResponse(
                loaded.revision(),
                sophonProperties.getHome(),
                serverAddress,
                serverPort,
                cfg.getAuthentication().isEnabled(),
                loaded.sourcePath().toString(),
                loaded.loadedAtEpochMs()
        ));
    }
}
