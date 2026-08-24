package com.sktpj.npcbrain;

final class ReplyTimerSchedulePolicy {
    private ReplyTimerSchedulePolicy() {}

    static long delayMs(long nowMs, long wakeAtMs) {
        if (wakeAtMs <= nowMs) return 0L;
        try {
            return Math.subtractExact(wakeAtMs, nowMs);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
