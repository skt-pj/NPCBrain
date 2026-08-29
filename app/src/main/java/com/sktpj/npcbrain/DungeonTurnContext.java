package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Pure-Java per-state runtime metadata. It never owns persistent dungeon data. */
final class DungeonTurnContext {
    static final class Snapshot {
        final String ownerNpcId;
        final long worldRevision;
        final int originalX;
        final int originalY;
        final List<DungeonActorContext> peers;

        Snapshot(
                String ownerNpcId,
                long worldRevision,
                int originalX,
                int originalY,
                List<DungeonActorContext> peers
        ) {
            this.ownerNpcId = NpcId.of(ownerNpcId).value();
            this.worldRevision = Math.max(0L, worldRevision);
            this.originalX = originalX;
            this.originalY = originalY;
            this.peers = peers == null ? new ArrayList<>() : peers;
        }
    }

    private static final WeakHashMap<DungeonState, Snapshot> CONTEXTS = new WeakHashMap<>();

    private DungeonTurnContext() {
    }

    static synchronized void register(
            DungeonState state,
            String ownerNpcId,
            long worldRevision,
            int originalX,
            int originalY,
            List<DungeonActorContext> peers
    ) {
        if (state == null) return;
        List<DungeonActorContext> copies = new ArrayList<>();
        if (peers != null) {
            for (DungeonActorContext peer : peers) {
                if (peer != null) copies.add(peer.copy());
            }
        }
        CONTEXTS.put(state, new Snapshot(
                ownerNpcId, worldRevision, originalX, originalY, copies));
    }

    static synchronized Snapshot lookup(DungeonState state) {
        if (state == null) return null;
        Snapshot snapshot = CONTEXTS.get(state);
        return snapshot == null
                ? new Snapshot("npc1", 0L, state.playerX, state.playerY, new ArrayList<>())
                : snapshot;
    }

    static synchronized List<DungeonActorContext> peers(DungeonState state) {
        Snapshot snapshot = lookup(state);
        List<DungeonActorContext> copies = new ArrayList<>();
        if (snapshot == null) return copies;
        for (DungeonActorContext peer : snapshot.peers) copies.add(peer.copy());
        return copies;
    }

    static synchronized boolean occupiedByPeer(DungeonState state, int x, int y) {
        Snapshot snapshot = lookup(state);
        if (snapshot == null) return false;
        for (DungeonActorContext peer : snapshot.peers) {
            if (peer.occupies(x, y)) return true;
        }
        return false;
    }

    static synchronized DungeonActorContext nearestLivingTarget(
            DungeonState state,
            int enemyX,
            int enemyY
    ) {
        if (state == null || state.hp <= 0) return nearestPeer(state, enemyX, enemyY);
        DungeonActorContext best = new DungeonActorContext(
                ownerNpcId(state), state.floor, state.playerX, state.playerY, state.hp, state.maxHp);
        int bestDistance = distance(enemyX, enemyY, state.playerX, state.playerY);
        Snapshot snapshot = lookup(state);
        if (snapshot == null) return best;
        for (DungeonActorContext peer : snapshot.peers) {
            if (!peer.alive() || peer.floor != state.floor) continue;
            int candidate = distance(enemyX, enemyY, peer.x, peer.y);
            if (candidate < bestDistance
                    || (candidate == bestDistance && peer.npcId.compareTo(best.npcId) < 0)) {
                best = peer.copy();
                bestDistance = candidate;
            }
        }
        return best;
    }

    static synchronized void applyDamage(
            DungeonState state,
            DungeonActorContext target,
            int damage
    ) {
        if (state == null || target == null || damage <= 0) return;
        Snapshot snapshot = lookup(state);
        if (snapshot == null || snapshot.ownerNpcId.equals(target.npcId)) {
            state.hp = Math.max(0, state.hp - damage);
            return;
        }
        for (DungeonActorContext peer : snapshot.peers) {
            if (peer.npcId.equals(target.npcId)) {
                peer.hp = Math.max(0, peer.hp - damage);
                return;
            }
        }
    }

    static synchronized boolean occupiedByAnyActorExcept(
            DungeonState state,
            String exceptNpcId,
            int x,
            int y
    ) {
        if (state == null) return false;
        Snapshot snapshot = lookup(state);
        String owner = snapshot == null ? "npc1" : snapshot.ownerNpcId;
        String except = exceptNpcId == null ? "" : exceptNpcId;
        if (!owner.equals(except)
                && state.hp > 0 && state.playerX == x && state.playerY == y) return true;
        if (snapshot == null) return false;
        for (DungeonActorContext peer : snapshot.peers) {
            if (!peer.npcId.equals(except) && peer.occupies(x, y)) return true;
        }
        return false;
    }

    private static DungeonActorContext nearestPeer(DungeonState state, int enemyX, int enemyY) {
        Snapshot snapshot = lookup(state);
        if (snapshot == null) return null;
        DungeonActorContext best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (DungeonActorContext peer : snapshot.peers) {
            if (!peer.alive()) continue;
            int candidate = distance(enemyX, enemyY, peer.x, peer.y);
            if (best == null || candidate < bestDistance
                    || (candidate == bestDistance && peer.npcId.compareTo(best.npcId) < 0)) {
                best = peer.copy();
                bestDistance = candidate;
            }
        }
        return best;
    }

    private static String ownerNpcId(DungeonState state) {
        Snapshot snapshot = lookup(state);
        return snapshot == null ? "npc1" : snapshot.ownerNpcId;
    }

    private static int distance(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }
}
