package com.sophon.knowledge.model;

/** 单次向量检索命中（含知识库名，供 RAG 引用展示）。 */
public record VectorSearchHit(String kbName, String chunkId, String content, double score) {}
