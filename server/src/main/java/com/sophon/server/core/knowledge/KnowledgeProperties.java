package com.sophon.server.core.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sophon.knowledge")
public class KnowledgeProperties {
    private int topK = 3;
    private int embeddingDim = 64;
    private int chunkSize = 400;
    private int overlap = 60;
    private double minScore = 0.08;
    private int rerankKeywordWeight = 1;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(int embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getRerankKeywordWeight() {
        return rerankKeywordWeight;
    }

    public void setRerankKeywordWeight(int rerankKeywordWeight) {
        this.rerankKeywordWeight = rerankKeywordWeight;
    }
}
