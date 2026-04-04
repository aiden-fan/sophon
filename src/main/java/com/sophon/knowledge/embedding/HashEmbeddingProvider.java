package com.sophon.knowledge.embedding;

import java.nio.charset.StandardCharsets;

/**
 * 确定性伪嵌入（L2 归一化），用于无云端密钥时的开发与单测；不替代真实向量模型。
 */
public final class HashEmbeddingProvider implements EmbeddingProvider {

    private final int dimensions;

    public HashEmbeddingProvider(int dimensions) {
        if (dimensions < 4) {
            throw new IllegalArgumentException("dimensions 过小");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        byte[] raw = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        float[] v = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            int h = mixHash(raw, i * 0x9E3779B9);
            v[i] = (float) ((h & 0xffff) / 65535.0 * 2.0 - 1.0);
        }
        normalizeL2(v);
        return v;
    }

    private static int mixHash(byte[] data, int seed) {
        int h = seed;
        for (byte b : data) {
            h = h * 31 + (b & 0xff);
            h ^= h >>> 16;
            h *= 0x85ebca6b;
        }
        return h;
    }

    private static void normalizeL2(float[] v) {
        double s = 0;
        for (float f : v) {
            s += (double) f * f;
        }
        s = Math.sqrt(s);
        if (s < 1e-8) {
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] /= (float) s;
        }
    }
}
