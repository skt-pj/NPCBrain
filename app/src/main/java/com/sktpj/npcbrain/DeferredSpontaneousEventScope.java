package com.sktpj.npcbrain;

final class DeferredSpontaneousEventScope {
    private static final ThreadLocal<String> EVENT_ID = new ThreadLocal<>();

    private DeferredSpontaneousEventScope() {}

    static void enter(String eventId) {
        String value = eventId == null ? "" : eventId.trim();
        if (value.isEmpty()) EVENT_ID.remove();
        else EVENT_ID.set(value);
    }

    static String currentEventId() {
        String value = EVENT_ID.get();
        return value == null ? "" : value;
    }

    static void exit() {
        EVENT_ID.remove();
    }
}
