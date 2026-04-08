package com.sophon.server.core.chat;

import com.sophon.common.dto.ChatRequest;
import com.sophon.common.dto.ChatResponse;
import com.sophon.common.dto.Citation;
import com.sophon.common.dto.SessionResponse;
import com.sophon.server.core.knowledge.KnowledgeService;
import com.sophon.server.core.llm.LLMProvider;
import com.sophon.server.core.llm.LLMService;
import com.sophon.server.core.multimodal.MultimodalService;
import com.sophon.server.infrastructure.store.MessageRepository;
import com.sophon.server.infrastructure.store.SessionRepository;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChatService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeService knowledgeService;
    private final MultimodalService multimodalService;
    private final LLMService llmService;

    public ChatService(
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            KnowledgeService knowledgeService,
            MultimodalService multimodalService,
            LLMService llmService
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.knowledgeService = knowledgeService;
        this.multimodalService = multimodalService;
        this.llmService = llmService;
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        SessionResponse session = loadSession(request.sessionId());
        String userInput = multimodalService.normalizeInput(request.message(), request.modalities());
        List<Citation> citations = knowledgeService.retrieve(
                session.id(),
                userInput,
                request.ragTopK(),
                request.ragMinScore()
        );
        List<LLMProvider.LLMMessage> messages = buildContext(session.id(), userInput);
        return llmService.generate(session.llmProvider(), session.llmModel(), messages)
                .map(answer -> persistAndBuildResponse(session.id(), userInput, answer, citations));
    }

    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        SessionResponse session = loadSession(request.sessionId());
        String userInput = multimodalService.normalizeInput(request.message(), request.modalities());
        List<Citation> citations = knowledgeService.retrieve(
                session.id(),
                userInput,
                request.ragTopK(),
                request.ragMinScore()
        );
        List<LLMProvider.LLMMessage> messages = buildContext(session.id(), userInput);
        long startMs = System.currentTimeMillis();
        String[] sink = new String[]{""};
        AtomicInteger chunkCount = new AtomicInteger(0);
        return llmService.streamGenerate(session.llmProvider(), session.llmModel(), messages)
                .map(chunk -> {
                    sink[0] += chunk;
                    chunkCount.incrementAndGet();
                    return ServerSentEvent.builder(chunk).event("chunk").build();
                })
                .concatWith(Mono.fromCallable(() -> {
                    String msgId = persist(session.id(), userInput, sink[0].trim(), citations);
                    int chars = sink[0].length();
                    int estTokens = Math.max(1, chars / 4);
                    long latency = System.currentTimeMillis() - startMs;
                    String doneJson = "{\"message_id\":\"" + msgId
                            + "\",\"chunks\":" + chunkCount.get()
                            + ",\"chars\":" + chars
                            + ",\"estimated_tokens\":" + estTokens
                            + ",\"latency_ms\":" + latency
                            + ",\"citations_count\":" + citations.size() + "}";
                    return ServerSentEvent.builder(doneJson).event("done").build();
                }));
    }

    private ChatResponse persistAndBuildResponse(String sessionId, String userInput, String answer, List<Citation> citations) {
        String msgId = persist(sessionId, userInput, answer, citations);
        return new ChatResponse(sessionId, msgId, answer, citations);
    }

    private String persist(String sessionId, String userInput, String answer, List<Citation> citations) {
        messageRepository.save(sessionId, "user", userInput, List.of());
        return messageRepository.save(sessionId, "assistant", answer, citations);
    }

    private List<LLMProvider.LLMMessage> buildContext(String sessionId, String currentInput) {
        List<LLMProvider.LLMMessage> list = new ArrayList<>();
        List<MessageRepository.MessageRow> recent = messageRepository.listRecent(sessionId, 8);
        for (int i = recent.size() - 1; i >= 0; i--) {
            MessageRepository.MessageRow row = recent.get(i);
            list.add(new LLMProvider.LLMMessage(row.role(), row.content()));
        }
        list.add(new LLMProvider.LLMMessage("user", currentInput));
        return list;
    }

    private SessionResponse loadSession(String sessionId) {
        return sessionRepository.get(sessionId).orElseThrow(() -> new IllegalArgumentException("session not found"));
    }
}
