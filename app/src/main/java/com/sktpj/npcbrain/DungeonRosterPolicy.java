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
        Set<String> allowed = allowedIds(activeRegistry);
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
        List<String> result = new ArrayList<>();
        Set<String> allowed = allowedIds(activeRegistry);
        if (allowed.contains("npc1")) result.add("npc1");
        if (allowed.contains("npc2")) result.add("npc2");
        if (result.isEmpty() && activeRegistry != null) {
            for (String raw : activeRegistry) {
                try {
                    String id = NpcId.of(raw).value();
                    if (allowed.contains(id)) {
                        result.add(id);
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return result;
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
        if (allowedIds(activeRegistry).contains(id)) result.add(id);
        return result;
    }

    private static Set<String> allowedIds(List<String> activeRegistry) {
        Set<String> allowed = new HashSet<>();
        if (activeRegistry == null) return allowed;
        for (String raw : activeRegistry) {
            try {
                allowed.add(NpcId.of(raw).value());
            } catch (Exception ignored) {
            }
        }
        return allowed;
    }
}
