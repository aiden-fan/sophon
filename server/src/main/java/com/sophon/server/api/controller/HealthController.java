package com.sophon.server.api.controller;

import com.sophon.common.dto.HealthResponse;
import com.sophon.server.infrastructure.config.LoadedServerConfig;
import com.sophon.server.infrastructure.config.SophonRuntimeConfigHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final SophonRuntimeConfigHolder holder;
    private final String applicationVersion;

    public HealthController(
            SophonRuntimeConfigHolder holder,
            @Value("${sophon.version:1.0.0-SNAPSHOT}") String applicationVersion
    ) {
        this.holder = holder;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping("/health")
    public Mono<HealthResponse> health() {
        LoadedServerConfig loaded = holder.current();
        return Mono.just(new HealthResponse(
                "ok",
                applicationVersion,
                Instant.now(),
                loaded.revision()
        ));
    }
}
