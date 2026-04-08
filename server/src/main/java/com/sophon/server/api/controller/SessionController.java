package com.sophon.server.api.controller;

import com.sophon.common.dto.SessionCreateRequest;
import com.sophon.common.dto.SessionResponse;
import com.sophon.server.infrastructure.store.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionRepository sessionRepository;

    public SessionController(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SessionResponse> create(@RequestBody(required = false) SessionCreateRequest request) {
        return Mono.fromCallable(() -> sessionRepository.create(request));
    }

    @GetMapping
    public Flux<SessionResponse> list() {
        return Flux.fromIterable(sessionRepository.list());
    }

    @GetMapping("/{sessionId}")
    public Mono<SessionResponse> get(@PathVariable String sessionId) {
        return Mono.justOrEmpty(sessionRepository.get(sessionId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found")));
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String sessionId) {
        if (!sessionRepository.delete(sessionId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
        }
        return Mono.empty();
    }
}
