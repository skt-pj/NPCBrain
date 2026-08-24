package com.sktpj.npcbrain;

import java.util.HashSet;
import java.util.Set;

final class ReplyTimerProcessingGate {
    private static final Set<String> RUNNING = new HashSet<>();

    private ReplyTimerProcessingGate() {}

    static synchronized boolean tryAcquire(String sourceKey) {
        String key = sourceKey == null ? "" : sourceKey.trim();
        if (key.isEmpty() || RUNNING.contains(key)) return false;
        RUNNING.add(key);
        return true;
    }

    static synchronized void release(String sourceKey) {
        String key = sourceKey == null ? "" : sourceKey.trim();
        if (!key.isEmpty()) RUNNING.remove(key);
    }

    static synchronized boolean isRunning(String sourceKey) {
        String key = sourceKey == null ? "" : sourceKey.trim();
        return !key.isEmpty() && RUNNING.contains(key);
    }
}
