package com.sktpj.npcbrain;

import java.util.Collections;
import java.util.List;

final class UiSelectionPolicy {
    private UiSelectionPolicy() {}

    static String resolve(String saved, String persisted, List<String> candidates) {
        List<String> safe = candidates == null ? Collections.emptyList() : candidates;
        if (contains(safe, saved)) return saved;
        if (contains(safe, persisted)) return persisted;
        return safe.isEmpty() ? "" : safe.get(0);
    }

    static String resolveDungeon(
            String saved,
            String persisted,
            String focused,
            List<String> active
    ) {
        List<String> safe = active == null ? Collections.emptyList() : active;
        if (contains(safe, saved)) return saved;
        if (contains(safe, persisted)) return persisted;
        if (contains(safe, focused)) return focused;
        return safe.isEmpty() ? "" : safe.get(0);
    }

    static String resolveRoom(String saved, String persisted, List<String> rooms) {
        List<String> safe = rooms == null ? Collections.emptyList() : rooms;
        if (contains(safe, saved)) return saved;
        if (contains(safe, persisted)) return persisted;
        return "";
    }

    private static boolean contains(List<String> values, String value) {
        return value != null && !value.trim().isEmpty() && values.contains(value.trim());
    }
}
