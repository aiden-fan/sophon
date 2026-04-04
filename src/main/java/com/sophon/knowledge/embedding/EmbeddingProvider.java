package com.sophon.knowledge.embedding;

/**
 * 文本嵌入；生产可换为 Dashscope/OpenAI 实现，测试可用 {@link HashEmbeddingProvider}。
 */
@FunctionalInterface
public interface EmbeddingProvider {

    float[] embed(String text);
}
