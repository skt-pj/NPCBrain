package com.sktpj.npcbrain;

final class DeferredSpontaneousSchedulePolicy {
    private DeferredSpontaneousSchedulePolicy() {}

    static long delayMs(long nowMs, long dueMs) {
        if (dueMs <= nowMs) return 0L;
        try {
            return Math.subtractExact(dueMs, nowMs);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
