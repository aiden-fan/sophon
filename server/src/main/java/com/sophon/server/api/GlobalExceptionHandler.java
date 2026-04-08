package com.sophon.server.api;

import com.sophon.common.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String msg = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        ApiErrorResponse body = new ApiErrorResponse(
                status.name().toLowerCase(),
                msg,
                exchange.getRequest().getPath().value()
        );
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleBadRequest(IllegalArgumentException ex, ServerWebExchange exchange) {
        ApiErrorResponse body = new ApiErrorResponse(
                "bad_request",
                ex.getMessage(),
                exchange.getRequest().getPath().value()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }
}
