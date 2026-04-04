package com.sophon.knowledge.index;

import java.util.ArrayList;
import java.util.List;

/** 将长文本切为固定上限的块，供向量化与入库。 */
public final class DocumentIndexer {

    private DocumentIndexer() {}

    public static List<String> chunkText(String text, int maxChars) {
        if (maxChars < 32) {
            throw new IllegalArgumentException("maxChars 过小");
        }
        String t = text == null ? "" : text.trim();
        List<String> out = new ArrayList<>();
        if (t.isEmpty()) {
            return out;
        }
        for (int i = 0; i < t.length(); i += maxChars) {
            out.add(t.substring(i, Math.min(t.length(), i + maxChars)));
        }
        return out;
    }
}
