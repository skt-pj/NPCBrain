package com.sktpj.npcbrain;

final class AiUsagePeriodPolicy {
    enum Action {
        MIGRATE,
        RESET,
        KEEP
    }

    private AiUsagePeriodPolicy() {
    }

    static int monthIndex(int year, int zeroBasedMonth) {
        if (zeroBasedMonth < 0 || zeroBasedMonth > 11) {
            throw new IllegalArgumentException("month must be 0..11");
        }
        return year * 12 + zeroBasedMonth;
    }

    static Action action(boolean hasStoredMonth, int storedMonthIndex, int currentMonthIndex) {
        if (!hasStoredMonth) return Action.MIGRATE;
        if (currentMonthIndex > storedMonthIndex) return Action.RESET;
        return Action.KEEP;
    }
}