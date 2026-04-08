package com.sophon.server.api.controller;

import com.sophon.common.dto.ChatRequest;
import com.sophon.common.dto.ChatResponse;
import com.sophon.server.core.chat.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.stream()) {
            return Mono.error(new IllegalArgumentException("Use /api/v1/chat/stream for stream=true"));
        }
        return chatService.chat(request);
    }

    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }
}
