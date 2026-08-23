package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DungeonRosterPolicy {
    static final int MAX_ACTIVE = 3;

    private DungeonRosterPolicy() {
    }

    static List<String> normalize(List<String> requested, List<String> activeRegistry) {
        List<String> result = new ArrayList<>();
        if (activeRegistry == null || activeRegistry.isEmpty()) return result;
        Set<String> allowed = new HashSet<>();
        for (String raw : activeRegistry) {
            try {
                allowed.add(NpcId.of(raw).value());
            } catch (Exception ignored) {
            }
        }
        if (requested != null) {
            for (String raw : requested) {
                if (result.size() >= MAX_ACTIVE) break;
                try {
                    String id = NpcId.of(raw).value();
                    if (allowed.contains(id) && !result.contains(id)) result.add(id);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    static List<String> initial(List<String> activeRegistry) {
        return normalize(activeRegistry, activeRegistry);
    }

    static List<String> toggle(List<String> current, String npcId, List<String> activeRegistry) {
        List<String> result = normalize(current, activeRegistry);
        final String id;
        try {
            id = NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return result;
        }
        if (result.remove(id)) return result;
        if (result.size() >= MAX_ACTIVE) return result;
        List<String> allowed = normalize(activeRegistry, activeRegistry);
        if (allowed.contains(id)) result.add(id);
        return result;
    }
}
