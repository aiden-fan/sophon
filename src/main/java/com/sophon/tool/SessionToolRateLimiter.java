package com.sophon.tool;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内按会话的 Tool 调用滑动窗口限流（每分钟调用次数）；与 {@link com.sophon.model.SessionCapabilityConfig} 中的上限配合。
 */
public final class SessionToolRateLimiter {

    private final Map<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    public boolean tryAcquire(String sessionId, int maxPerMinute) {
        if (maxPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> q = calls.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) {
                q.pollFirst();
            }
            if (q.size() >= maxPerMinute) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
