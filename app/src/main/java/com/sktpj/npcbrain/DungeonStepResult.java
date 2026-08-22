package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DungeonStepResult {
    final DungeonState state;
    final List<DungeonCombatEvent> events;

    DungeonStepResult(DungeonState state, List<DungeonCombatEvent> events) {
        this.state = state;
        List<DungeonCombatEvent> copy = events == null
                ? new ArrayList<>()
                : new ArrayList<>(events);
        this.events = Collections.unmodifiableList(copy);
    }
}
