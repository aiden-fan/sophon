package com.sophon.knowledge.rag;

import com.sophon.ai.dto.LlmMessage;
import com.sophon.core.session.SessionManager;
import com.sophon.knowledge.KnowledgeManager;
import com.sophon.knowledge.model.VectorSearchHit;
import com.sophon.model.SessionCapabilityConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 检索增强：仅使用本会话 {@link SessionCapabilityConfig#getAttachedKnowledgeBaseNames()} 绑定的库；
 * 引用列表受 {@link SessionCapabilityConfig#isCitationsEnabled()} 控制。
 */
public final class RAGEngine {

    private final KnowledgeManager knowledge;
    private final SessionManager sessions;
    private final int topKPerKb;
    private final int maxHitsTotal;

    public RAGEngine(KnowledgeManager knowledge, SessionManager sessions, int topKPerKb, int maxHitsTotal) {
        this.knowledge = knowledge;
        this.sessions = sessions;
        this.topKPerKb = Math.max(1, topKPerKb);
        this.maxHitsTotal = Math.max(1, maxHitsTotal);
    }

    public RAGEngine(KnowledgeManager knowledge, SessionManager sessions) {
        this(knowledge, sessions, 3, 8);
    }

    /**
     * 在已有消息列表上插入一条仅对本请求生效的 system 片段（含检索块）；不改变持久化历史。
     */
    public List<LlmMessage> augmentForChat(String sessionId, List<LlmMessage> messages) {
        SessionCapabilityConfig cap =
                sessions.getSession(sessionId).orElseThrow().getCapabilities();
        var kbs = cap.getAttachedKnowledgeBaseNames();
        if (kbs == null || kbs.isEmpty()) {
            return messages;
        }
        String query = lastUserContent(messages);
        if (query.isBlank()) {
            return messages;
        }
        List<VectorSearchHit> hits = new ArrayList<>();
        for (String kb : kbs) {
            hits.addAll(knowledge.search(kb, query, topKPerKb));
        }
        if (hits.isEmpty()) {
            return messages;
        }
        hits.sort(Comparator.comparingDouble(VectorSearchHit::score).reversed());
        if (hits.size() > maxHitsTotal) {
            hits = new ArrayList<>(hits.subList(0, maxHitsTotal));
        }
        String ragBlock = buildRagSystemText(hits, cap.isCitationsEnabled());
        return insertRagSystem(messages, ragBlock);
    }

    private static String lastUserContent(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage m = messages.get(i);
            if ("user".equals(m.role()) && !m.content().isBlank()) {
                return m.content();
            }
        }
        return "";
    }

    private static String buildRagSystemText(List<VectorSearchHit> hits, boolean citations) {
        StringBuilder body = new StringBuilder();
        body.append("【知识库检索】以下片段来自本会话已绑定的知识库，请在回答中合理运用；若与问题无关可忽略。\n");
        int n = 0;
        for (VectorSearchHit h : hits) {
            n++;
            body.append("\n---\n");
            if (citations) {
                body.append("[#").append(n).append("] kb=").append(h.kbName()).append(" chunk=").append(h.chunkId()).append("\n");
            }
            body.append(h.content());
        }
        if (citations) {
            body.append("\n\n【引用说明】已在各片段前标注 chunk id；用户开启 citations 时请保持引用可对应。");
        }
        return body.toString();
    }

    private static List<LlmMessage> insertRagSystem(List<LlmMessage> messages, String ragBlock) {
        List<LlmMessage> out = new ArrayList<>();
        if (messages.isEmpty()) {
            out.add(LlmMessage.system(ragBlock));
            return out;
        }
        if ("system".equals(messages.get(0).role())) {
            out.add(messages.get(0));
            out.add(LlmMessage.system(ragBlock));
            for (int i = 1; i < messages.size(); i++) {
                out.add(messages.get(i));
            }
        } else {
            out.add(LlmMessage.system(ragBlock));
            out.addAll(messages);
        }
        return List.copyOf(out);
    }
}
