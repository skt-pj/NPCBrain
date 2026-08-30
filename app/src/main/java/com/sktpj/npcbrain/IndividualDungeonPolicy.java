package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.List;

final class IndividualDungeonPolicy {
    static final int MAX_SLOTS = 8;

    private IndividualDungeonPolicy() {
    }

    static List<String> visibleNpcIds(List<String> presentNpcIds) {
        List<String> result = new ArrayList<>();
        if (presentNpcIds == null) return result;
        for (String raw : presentNpcIds) {
            if (result.size() >= MAX_SLOTS) break;
            try {
                String id = NpcId.of(raw).value();
                if (!result.contains(id)) result.add(id);
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
