package com.sophon.server.api.filter;

import com.sophon.server.infrastructure.config.SophonRuntimeConfig;
import com.sophon.server.infrastructure.config.SophonRuntimeConfigHolder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Optional bearer / header token and client IP allowlist from {@code server.yaml}.
 */
@Component
@Order(50)
public class TokenAuthWebFilter implements WebFilter {

    private static final String HEADER_TOKEN = "X-Sophon-Token";

    private final SophonRuntimeConfigHolder holder;

    public TokenAuthWebFilter(SophonRuntimeConfigHolder holder) {
        this.holder = holder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/v1")) {
            return chain.filter(exchange);
        }
        if ("/api/v1/health".equals(path)) {
            return chain.filter(exchange);
        }

        SophonRuntimeConfig cfg = holder.config();
        SophonRuntimeConfig.AuthenticationSection auth = cfg.getAuthentication();
        if (!auth.isEnabled()) {
            return chain.filter(exchange);
        }

        if (!isIpAllowed(exchange, auth.getAllowedClients())) {
            return jsonError(exchange, HttpStatus.FORBIDDEN, "forbidden", "client ip not allowed");
        }

        String expected = auth.getToken();
        if (expected == null || expected.isBlank()) {
            return jsonError(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "authentication token not configured");
        }

        String bearer = extractBearer(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        String header = exchange.getRequest().getHeaders().getFirst(HEADER_TOKEN);
        String provided = bearer != null ? bearer : header;
        if (provided != null && constantTimeEquals(provided, expected)) {
            return chain.filter(exchange);
        }
        return jsonError(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "invalid or missing token");
    }

    private static boolean isIpAllowed(ServerWebExchange exchange, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        String host = remote.getAddress().getHostAddress();
        for (String a : allowed) {
            if (a != null && a.equals(host)) {
                return true;
            }
            try {
                InetAddress allowedAddr = InetAddress.getByName(a.trim());
                if (allowedAddr.equals(remote.getAddress())) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static String extractBearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }

    private static Mono<Void> jsonError(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"" + escapeJson(error) + "\",\"message\":\"" + escapeJson(message) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
