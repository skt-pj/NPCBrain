package com.sktpj.npcbrain;

final class ReplyTimerExecutionScope {
    private static final ThreadLocal<String> SOURCE_KEY = new ThreadLocal<>();

    private ReplyTimerExecutionScope() {}

    static void enter(String sourceKey) {
        String key = sourceKey == null ? "" : sourceKey.trim();
        if (key.isEmpty()) SOURCE_KEY.remove();
        else SOURCE_KEY.set(key);
    }

    static String currentSourceKey() {
        String value = SOURCE_KEY.get();
        return value == null ? "" : value;
    }

    static void exit() {
        SOURCE_KEY.remove();
    }
}
