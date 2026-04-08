package com.sophon.server.api.controller;

import com.sophon.server.core.knowledge.KnowledgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) {
        String sourceId = String.valueOf(body.getOrDefault("sourceId", ""));
        String title = String.valueOf(body.getOrDefault("title", ""));
        String uri = String.valueOf(body.getOrDefault("uri", ""));
        String content = String.valueOf(body.getOrDefault("content", ""));
        String chunkId = knowledgeService.ingest(sourceId, title, uri, content);
        return Map.of("chunkId", chunkId, "ok", true);
    }
}
