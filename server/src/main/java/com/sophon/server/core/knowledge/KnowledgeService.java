package com.sophon.server.core.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.common.dto.Citation;
import com.sophon.server.infrastructure.store.KnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeService {

    private static final TypeReference<List<Double>> VECTOR_TYPE = new TypeReference<>() {};

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeProperties properties, ObjectMapper objectMapper) {
        this.knowledgeRepository = knowledgeRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<Citation> retrieve(String sessionId, String query) {
        return retrieve(sessionId, query, null, null);
    }

    public List<Citation> retrieve(String sessionId, String query, Integer topKOverride, Double minScoreOverride) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int topK = topKOverride == null ? Math.max(1, properties.getTopK()) : Math.max(1, topKOverride);
        double minScore = minScoreOverride == null ? properties.getMinScore() : minScoreOverride;
        double[] q = embed(query);
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeRepository.ChunkRow row : knowledgeRepository.listAll()) {
            double[] v = readVector(row.embeddingJson());
            double sim = cosine(q, v);
            int keywordHits = keywordHits(query, row.content());
            double finalScore = sim + keywordHits * 0.01 * properties.getRerankKeywordWeight();
            if (finalScore >= minScore) {
                scored.add(new Scored(row, finalScore));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .map(s -> new Citation(
                        s.row.id(),
                        s.row.title(),
                        s.row.uri(),
                        s.row.content().length() <= 160 ? s.row.content() : s.row.content().substring(0, 160) + "..."
                ))
                .toList();
    }

    public String ingest(String sourceId, String title, String uri, String content) {
        String src = (sourceId == null || sourceId.isBlank()) ? "src_" + UUID.randomUUID().toString().substring(0, 8) : sourceId;
        String text = content == null ? "" : content;
        List<String> chunks = splitChunks(text);
        String firstId = null;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            double[] emb = embed(chunk);
            String id = knowledgeRepository.insertChunk(
                    src,
                    (title == null ? "" : title) + " #" + (i + 1),
                    uri,
                    chunk,
                    writeVector(emb)
            );
            if (firstId == null) {
                firstId = id;
            }
        }
        return firstId == null ? "" : firstId;
    }

    private List<String> splitChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        int chunk = Math.max(80, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(chunk / 2, properties.getOverlap()));
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunk);
            out.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return out;
    }

    private int keywordHits(String query, String content) {
        if (query == null || content == null) {
            return 0;
        }
        String lc = content.toLowerCase();
        int hits = 0;
        for (String token : query.toLowerCase().split("\\W+")) {
            if (token.isBlank() || token.length() < 2) {
                continue;
            }
            if (lc.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private double[] embed(String text) {
        int dim = Math.max(8, properties.getEmbeddingDim());
        double[] vec = new double[dim];
        if (text == null) {
            return vec;
        }
        String[] tokens = text.toLowerCase().split("\\W+");
        for (String t : tokens) {
            if (t.isBlank()) {
                continue;
            }
            int h = Math.abs(t.hashCode());
            int idx = h % dim;
            vec[idx] += 1.0;
        }
        return normalize(vec);
    }

    private static double[] normalize(double[] vec) {
        double sum = 0.0;
        for (double v : vec) {
            sum += v * v;
        }
        if (sum <= 0) {
            return vec;
        }
        double n = Math.sqrt(sum);
        double[] out = new double[vec.length];
        for (int i = 0; i < vec.length; i++) {
            out[i] = vec[i] / n;
        }
        return out;
    }

    private static double cosine(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    private double[] readVector(String json) {
        try {
            List<Double> list = objectMapper.readValue(json, VECTOR_TYPE);
            double[] out = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                out[i] = list.get(i);
            }
            return out;
        } catch (Exception e) {
            return new double[Math.max(8, properties.getEmbeddingDim())];
        }
    }

    private String writeVector(double[] vector) {
        try {
            List<Double> list = new ArrayList<>(vector.length);
            for (double v : vector) {
                list.add(v);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private record Scored(KnowledgeRepository.ChunkRow row, double score) {}
}
