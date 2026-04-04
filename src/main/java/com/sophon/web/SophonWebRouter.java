package com.sophon.web;

import com.sophon.ai.dto.ModelOutputKind;
import com.sophon.ai.dto.StreamingChunk;
import com.sophon.bootstrap.SophonBootstrap;
import com.sophon.model.Session;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * 阶段 16：与 CLI 共用 {@link SophonBootstrap.Handle} 的 WebFlux 路由。
 */
public final class SophonWebRouter {

    private SophonWebRouter() {}

    public static RouterFunction<ServerResponse> build(SophonBootstrap.Handle h) {
        return route(
                        POST("/api/sessions"),
                        req ->
                                Mono.fromCallable(
                                                () -> {
                                                    Session s = h.sessions().createSession("web");
                                                    return Map.<String, Object>of(
                                                            "id", s.getId(), "title", s.getTitle() != null ? s.getTitle() : "");
                                                })
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .flatMap(body -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body)))
                .andRoute(
                        GET("/api/sessions"),
                        req ->
                                Mono.fromCallable(
                                                () ->
                                                        h.sessions().listSessions().stream()
                                                                .map(
                                                                        s ->
                                                                                Map.of(
                                                                                        "id",
                                                                                        s.getId(),
                                                                                        "title",
                                                                                        s.getTitle() != null ? s.getTitle() : ""))
                                                                .collect(Collectors.toList()))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .flatMap(body -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body)))
                .andRoute(
                        POST("/api/sessions/{id}/chat/stream"),
                        req -> {
                            String sid = req.pathVariable("id");
                            return req.bodyToMono(ChatRequestBody.class)
                                    .flatMap(
                                            body -> {
                                                String text = body.text() != null ? body.text() : "";
                                                Flux<String> flux =
                                                        h.chat()
                                                                .chatStream(sid, text)
                                                                .filter(
                                                                        c ->
                                                                                c instanceof StreamingChunk.TextToken
                                                                                        && ((StreamingChunk.TextToken) c).kind()
                                                                                                == ModelOutputKind.FINAL)
                                                                .map(c -> ((StreamingChunk.TextToken) c).text());
                                                return ServerResponse.ok()
                                                        .contentType(MediaType.TEXT_PLAIN)
                                                        .body(flux, String.class);
                                            });
                        })
                .andRoute(
                        POST("/api/knowledge/{kb}/ingest"),
                        req -> {
                            String kb = req.pathVariable("kb");
                            return req.bodyToMono(String.class)
                                    .flatMap(
                                            text ->
                                                    Mono.fromRunnable(
                                                                    () ->
                                                                            h.knowledge()
                                                                                    .ingestText(
                                                                                            kb,
                                                                                            "http-ingest",
                                                                                            text != null ? text : ""))
                                                            .subscribeOn(Schedulers.boundedElastic())
                                                            .then(
                                                                    Mono.defer(
                                                                            () ->
                                                                                    ServerResponse.ok()
                                                                                            .bodyValue(
                                                                                                    Map.of(
                                                                                                            "ok",
                                                                                                            true)))));
                        });
    }

    public record ChatRequestBody(String text) {}
}
