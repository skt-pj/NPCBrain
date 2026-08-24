package com.sktpj.npcbrain;

final class ReplyTimerPolicy {
    static final long MIN_DELAY_MS = 30_000L;
    static final long MAX_DELAY_MS = 7L * 24L * 60L * 60L * 1000L;

    private ReplyTimerPolicy() {}

    static boolean isValidWake(long nowMs, long wakeAtMs) {
        if (nowMs <= 0L || wakeAtMs <= nowMs) return false;
        long delay;
        try {
            delay = Math.subtractExact(wakeAtMs, nowMs);
        } catch (ArithmeticException ignored) {
            return false;
        }
        return delay >= MIN_DELAY_MS && delay <= MAX_DELAY_MS;
    }

    static boolean isDue(ReplyTimerTask task, long nowMs) {
        return task != null && task.isValid() && nowMs >= task.wakeAtMs;
    }

    static String delayedReplyMessageId(ReplyTimerTask task) {
        if (task == null) return "";
        return "reply_timer_" + safe(task.npcId) + "_" + safe(task.sourceMessageId);
    }

    static String decisionMessageId(ReplyTimerTask task, String suffix) {
        if (task == null) return "";
        return "reply_timer_decision_" + task.jobId + "_" + safe(suffix);
    }

    private static String safe(String value) {
        String text = value == null ? "" : value.trim();
        return text.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
