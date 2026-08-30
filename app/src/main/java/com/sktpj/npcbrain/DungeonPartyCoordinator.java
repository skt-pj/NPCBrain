package com.sktpj.npcbrain;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

final class DungeonPartyCoordinator {
    private static final int MAX_COHESION_DISTANCE = 5;

    private final DungeonRosterStore rosterStore;
    private final DungeonStore dungeonStore;

    DungeonPartyCoordinator(Context context) {
        Context app = context.getApplicationContext();
        rosterStore = new DungeonRosterStore(app);
        dungeonStore = new DungeonStore(app);
    }

    synchronized void reconcile(String preferredLeaderId) {
        List<String> party = rosterStore.activeNpcIds();
        if (party.isEmpty()) return;
        String leaderId = party.contains(preferredLeaderId) ? preferredLeaderId : party.get(0);

        int targetFloor = 1;
        int targetTurn = 0;
        long sourceSeed = 1L;
        DungeonState source = null;
        for (String npcId : party) {
            DungeonState member = dungeonStore.load(npcId);
            if (member == null || member.hp <= 0) continue;
            if (source == null || member.floor > targetFloor) {
                source = member;
                targetFloor = member.floor;
                sourceSeed = member.seed;
            }
            targetFloor = Math.max(targetFloor, member.floor);
            targetTurn = Math.max(targetTurn, member.turn);
        }

        for (String npcId : party) {
            DungeonState member = dungeonStore.load(npcId);
            if (member != null && member.hp <= 0) continue;
            if (member == null || member.floor != targetFloor) {
                int maxHp = member == null ? 10 : member.maxHp;
                int hp = member == null ? maxHp : member.hp;
                long seed = source == null
                        ? DungeonGenerator.nextFloorSeed(sourceSeed ^ npcId.hashCode(), targetFloor)
                        : sourceSeed;
                DungeonState joined = DungeonGenerator.generate(
                        seed, targetFloor, maxHp, hp, Math.max(targetTurn, member == null ? 0 : member.turn));
                joined.lastAction = "パーティに合流 → " + targetFloor + "F";
                dungeonStore.save(npcId, joined);
            }
        }

        DungeonState leader = dungeonStore.load(leaderId);
        if (leader == null || leader.hp <= 0 || leader.floor != targetFloor) return;
        for (String npcId : party) {
            if (npcId.equals(leaderId)) continue;
            DungeonState member = dungeonStore.load(npcId);
            if (member == null || member.hp <= 0 || member.floor != leader.floor) continue;
            int distance = Math.abs(member.playerX - leader.playerX)
                    + Math.abs(member.playerY - leader.playerY);
            if (distance <= MAX_COHESION_DISTANCE) continue;
            int[] join = nearestPartyCell(member, leader.playerX, leader.playerY);
            if (join == null) continue;
            member.playerX = join[0];
            member.playerY = join[1];
            member.markVisited(join[0], join[1]);
            member.lastAction = "パーティの近くへ合流";
            dungeonStore.save(npcId, member);
        }
    }

    private static int[] nearestPartyCell(DungeonState state, int leaderX, int leaderY) {
        List<int[]> candidates = new ArrayList<>();
        for (int radius = 1; radius <= 3; radius++) {
            for (int y = Math.max(0, leaderY - radius); y <= Math.min(state.height - 1, leaderY + radius); y++) {
                for (int x = Math.max(0, leaderX - radius); x <= Math.min(state.width - 1, leaderX + radius); x++) {
                    if (Math.abs(x - leaderX) + Math.abs(y - leaderY) != radius) continue;
                    int tile = state.tileAt(x, y);
                    if (!state.walkable(x, y)
                            || tile == DungeonState.STAIRS
                            || tile == DungeonState.CHEST) continue;
                    if (state.enemyAt(x, y) != null || DungeonTurnContext.occupiedByPeer(state, x, y)) continue;
                    candidates.add(new int[]{x, y});
                }
            }
            if (!candidates.isEmpty()) return candidates.get(0);
        }
        return null;
    }
}
