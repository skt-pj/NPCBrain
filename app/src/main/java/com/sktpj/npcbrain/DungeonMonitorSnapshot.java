package com.sktpj.npcbrain;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

final class DungeonMonitorSnapshot {
    final DungeonState state;
    final List<DungeonActorContext> peers;

    DungeonMonitorSnapshot(DungeonState state, List<DungeonActorContext> peers) {
        this.state = state;
        this.peers = peers == null ? new ArrayList<>() : peers;
    }

    static DungeonMonitorSnapshot load(Context context, String npcId) {
        String owner = NpcId.of(npcId).value();
        DungeonStore dungeonStore = new DungeonStore(context);
        DungeonState raw = dungeonStore.loadRaw(owner);
        if (raw == null) return new DungeonMonitorSnapshot(null, new ArrayList<>());

        DungeonSharedFloor shared = new DungeonWorldStore(context).load(raw.floor);
        DungeonState state = shared == null ? raw : shared.attach(raw, false);
        if (state == null) state = raw;

        List<DungeonActorContext> peers = new ArrayList<>();
        DungeonPresenceStore presence = new DungeonPresenceStore(context);
        for (String otherId : presence.activePresentNpcIds()) {
            if (owner.equals(otherId)) continue;
            DungeonState other = dungeonStore.loadRaw(otherId);
            if (other == null || other.hp <= 0 || other.floor != state.floor) continue;
            peers.add(new DungeonActorContext(
                    otherId,
                    other.floor,
                    other.playerX,
                    other.playerY,
                    other.hp,
                    other.maxHp));
        }

        // Read-only render metadata: lets DungeonBoardView's existing Game Boy peer overlay
        // use the same same-floor actor context as the party board without advancing or saving.
        DungeonTurnContext.register(
                state,
                owner,
                shared == null ? 0L : shared.revision,
                state.playerX,
                state.playerY,
                peers);
        return new DungeonMonitorSnapshot(state, peers);
    }
}
