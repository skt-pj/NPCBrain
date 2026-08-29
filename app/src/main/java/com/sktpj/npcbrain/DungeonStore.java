package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Compatibility facade for dungeon persistence.
 *
 * v0.4.40 keeps actor progress under the existing per-NPC keys, but map/seed/enemies are
 * canonicalized through one application-wide DungeonWorldStore for each floor.
 */
final class DungeonStore {
    private static final String PREFS = "npcbrain_dungeon_v1";
    private static final Object WORLD_LOCK = new Object();
    private static final WeakHashMap<DungeonState, DungeonStore> STATE_STORES = new WeakHashMap<>();

    private final Context appContext;
    private final SharedPreferences preferences;
    private final DungeonWorldStore worldStore;
    private final NpcRegistryStore registryStore;

    DungeonStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        worldStore = new DungeonWorldStore(appContext);
        registryStore = new NpcRegistryStore(appContext);
    }

    DungeonState load(String npcId) {
        String id = NpcId.of(npcId).value();
        synchronized (WORLD_LOCK) {
            DungeonState raw = loadRaw(id);
            if (raw == null) return null;
            DungeonSharedFloor shared = ensureFloor(raw.floor, raw);
            if (shared == null) return raw;

            List<DungeonActorContext> actors = normalizeActorsOnFloor(shared.floor, shared);
            DungeonActorContext current = actorById(actors, id);
            DungeonState combined = shared.attach(raw, false);
            if (combined == null) return raw;
            if (current != null) {
                combined.playerX = current.x;
                combined.playerY = current.y;
                combined.hp = current.hp;
                combined.markVisited(current.x, current.y);
            }
            registerState(combined, id, shared.revision, peersExcept(actors, id));
            return combined;
        }
    }

    void save(String npcId, DungeonState state) {
        if (state == null) return;
        String id = NpcId.of(npcId).value();
        synchronized (WORLD_LOCK) {
            DungeonState priorRaw = loadRaw(id);
            boolean enteredNewFloor = priorRaw == null || priorRaw.floor != state.floor;
            DungeonTurnContext.Snapshot turnContext = DungeonTurnContext.lookup(state);

            persistPeerDamage(turnContext);

            DungeonSharedFloor shared = ensureFloor(state.floor, state);
            if (shared == null) {
                saveRawInternal(id, state);
                registerState(state, id, 0L, new ArrayList<>());
                archiveIfDead(id, state);
                return;
            }

            boolean stateHasCurrentWorld = turnContext != null
                    && turnContext.worldRevision == shared.revision
                    && turnContext.ownerNpcId.equals(id)
                    && state.floor == shared.floor;
            if (stateHasCurrentWorld && !shared.sameWorld(state)) {
                DungeonSharedFloor updated = shared.withWorld(state);
                if (updated != null && worldStore.save(updated)) shared = updated;
            } else if (!shared.sameWorld(state)) {
                shared.overwriteSharedPart(state);
            }

            if (enteredNewFloor) {
                for (int y = 0; y < state.visited.length; y++) {
                    java.util.Arrays.fill(state.visited[y], false);
                }
                state.playerX = shared.entryX;
                state.playerY = shared.entryY;
                state.markVisited(state.playerX, state.playerY);
            }

            shared.overwriteSharedPart(state);
            List<DungeonActorContext> peers = actorContextsOnFloorExcept(id, state.floor);
            int originalX = turnContext == null ? state.playerX : turnContext.originalX;
            int originalY = turnContext == null ? state.playerY : turnContext.originalY;
            legalizeActorPosition(id, state, shared, peers, originalX, originalY);

            DungeonState canonicalForStorage = shared.attach(state, false);
            if (canonicalForStorage == null) canonicalForStorage = state;
            canonicalForStorage.playerX = state.playerX;
            canonicalForStorage.playerY = state.playerY;
            canonicalForStorage.hp = state.hp;
            canonicalForStorage.turn = state.turn;
            canonicalForStorage.lastAction = state.lastAction;
            copyVisited(state.visited, canonicalForStorage.visited);
            saveRawInternal(id, canonicalForStorage);

            List<DungeonActorContext> allActors = normalizeActorsOnFloor(state.floor, shared);
            DungeonActorContext current = actorById(allActors, id);
            if (current != null) {
                state.playerX = current.x;
                state.playerY = current.y;
                state.hp = current.hp;
            }
            shared.overwriteSharedPart(state);
            registerState(state, id, shared.revision, peersExcept(allActors, id));
            archiveIfDead(id, state);
        }
    }

    void clear(String npcId) {
        String id = NpcId.of(npcId).value();
        synchronized (WORLD_LOCK) {
            preferences.edit().remove(key(id)).apply();
        }
    }

    static Object sharedTurnLock() {
        return WORLD_LOCK;
    }

    /** Refreshes a long-lived Activity/background state immediately before Engine resolution. */
    static void refreshSharedWorldForTurn(DungeonState state) {
        if (state == null) return;
        synchronized (WORLD_LOCK) {
            DungeonStore store = storeFor(state);
            DungeonTurnContext.Snapshot metadata = DungeonTurnContext.lookup(state);
            if (store == null || metadata == null) return;
            store.refreshForTurn(metadata.ownerNpcId, state);
        }
    }

    /** Commits one same-floor Engine result before another NPC can start from stale enemy data. */
    static void commitSharedTurn(DungeonState state) {
        if (state == null) return;
        synchronized (WORLD_LOCK) {
            DungeonStore store = storeFor(state);
            DungeonTurnContext.Snapshot metadata = DungeonTurnContext.lookup(state);
            if (store == null || metadata == null) return;
            store.save(metadata.ownerNpcId, state);
        }
    }

    /** Connects an actor's generated next-floor proposal to the one canonical destination floor. */
    static void commitFloorTransition(DungeonState previous, DungeonState next) {
        if (previous == null || next == null) return;
        synchronized (WORLD_LOCK) {
            DungeonStore store = storeFor(previous);
            DungeonTurnContext.Snapshot metadata = DungeonTurnContext.lookup(previous);
            if (store == null || metadata == null) return;
            DungeonTurnContext.register(
                    next,
                    metadata.ownerNpcId,
                    0L,
                    next.playerX,
                    next.playerY,
                    new ArrayList<>());
            synchronized (STATE_STORES) {
                STATE_STORES.put(next, store);
            }
            store.save(metadata.ownerNpcId, next);
        }
    }

    static List<DungeonActorContext> visiblePeerCandidates(DungeonState state) {
        return DungeonTurnContext.peers(state);
    }

    DungeonState loadRaw(String npcId) {
        String raw = preferences.getString(key(npcId), "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return DungeonState.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    void saveRaw(String npcId, DungeonState state) {
        if (state == null) return;
        synchronized (WORLD_LOCK) {
            saveRawInternal(NpcId.of(npcId).value(), state);
        }
    }

    private static DungeonStore storeFor(DungeonState state) {
        synchronized (STATE_STORES) {
            return STATE_STORES.get(state);
        }
    }

    private void refreshForTurn(String npcId, DungeonState state) {
        DungeonSharedFloor shared = ensureFloor(state.floor, state);
        if (shared == null) return;
        List<DungeonActorContext> actors = normalizeActorsOnFloor(state.floor, shared);
        DungeonActorContext current = actorById(actors, npcId);
        shared.overwriteSharedPart(state);
        if (current != null) {
            state.playerX = current.x;
            state.playerY = current.y;
            state.hp = current.hp;
            state.markVisited(current.x, current.y);
        }
        registerState(state, npcId, shared.revision, peersExcept(actors, npcId));
    }

    private DungeonSharedFloor ensureFloor(int floor, DungeonState proposal) {
        DungeonSharedFloor existing = worldStore.load(floor);
        if (existing != null) return existing;

        DungeonState canonical = canonicalLegacyState(floor);
        if (canonical == null && proposal != null && proposal.floor == floor) canonical = proposal;
        if (canonical == null) {
            long seed = 0x4e5043425241494eL ^ (0x9E3779B97F4A7C15L * Math.max(1, floor));
            canonical = DungeonGenerator.generate(seed, floor);
        }
        DungeonSharedFloor created = DungeonSharedFloor.fromState(
                canonical, canonical.playerX, canonical.playerY, 1L);
        if (created == null) return null;
        worldStore.save(created);
        return created;
    }

    private DungeonState canonicalLegacyState(int floor) {
        List<String> ids = new ArrayList<>(registryStore.npcIds());
        Collections.sort(ids);
        for (String id : ids) {
            DungeonState candidate = loadRaw(id);
            if (candidate != null && candidate.floor == floor && candidate.hp > 0) return candidate;
        }
        return null;
    }

    private List<DungeonActorContext> normalizeActorsOnFloor(
            int floor,
            DungeonSharedFloor shared
    ) {
        List<String> ids = new ArrayList<>(registryStore.activeNpcIds());
        Collections.sort(ids);
        List<DungeonActorContext> actors = new ArrayList<>();
        for (String id : ids) {
            DungeonState raw = loadRaw(id);
            if (raw == null || raw.floor != floor || raw.hp <= 0) continue;
            DungeonState canonical = shared.attach(raw, false);
            if (canonical == null) continue;
            int[] position = legalPosition(
                    canonical.playerX,
                    canonical.playerY,
                    shared,
                    actors,
                    canonical.playerX,
                    canonical.playerY);
            boolean moved = position[0] != canonical.playerX || position[1] != canonical.playerY;
            canonical.playerX = position[0];
            canonical.playerY = position[1];
            canonical.markVisited(position[0], position[1]);
            if (moved || !shared.sameWorld(raw)) saveRawInternal(id, canonical);
            actors.add(new DungeonActorContext(
                    id, floor, canonical.playerX, canonical.playerY, canonical.hp, canonical.maxHp));
        }
        return actors;
    }

    private List<DungeonActorContext> actorContextsOnFloorExcept(String npcId, int floor) {
        List<DungeonActorContext> peers = new ArrayList<>();
        for (String id : registryStore.activeNpcIds()) {
            if (id.equals(npcId)) continue;
            DungeonState raw = loadRaw(id);
            if (raw == null || raw.floor != floor || raw.hp <= 0) continue;
            peers.add(new DungeonActorContext(
                    id, floor, raw.playerX, raw.playerY, raw.hp, raw.maxHp));
        }
        peers.sort(Comparator.comparing(actor -> actor.npcId));
        return peers;
    }

    private void legalizeActorPosition(
            String npcId,
            DungeonState state,
            DungeonSharedFloor shared,
            List<DungeonActorContext> peers,
            int originalX,
            int originalY
    ) {
        if (isLegalActorCell(state.playerX, state.playerY, shared, peers)) return;
        if (isLegalActorCell(originalX, originalY, shared, peers)) {
            state.playerX = originalX;
            state.playerY = originalY;
            state.markVisited(originalX, originalY);
            state.lastAction = "移動先に他の冒険者がいるため待機";
            return;
        }
        int[] position = legalPosition(
                state.playerX,
                state.playerY,
                shared,
                peers,
                shared.entryX,
                shared.entryY);
        state.playerX = position[0];
        state.playerY = position[1];
        state.markVisited(position[0], position[1]);
        state.lastAction = "共有ダンジョンの空きマスへ移動";
    }

    private static int[] legalPosition(
            int preferredX,
            int preferredY,
            DungeonSharedFloor shared,
            List<DungeonActorContext> occupied,
            int fallbackX,
            int fallbackY
    ) {
        if (isLegalActorCell(preferredX, preferredY, shared, occupied)) {
            return new int[]{preferredX, preferredY};
        }
        int maxRadius = shared.width() + shared.height();
        int originX = shared.walkable(fallbackX, fallbackY) ? fallbackX : shared.entryX;
        int originY = shared.walkable(fallbackX, fallbackY) ? fallbackY : shared.entryY;
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int y = 0; y < shared.height(); y++) {
                for (int x = 0; x < shared.width(); x++) {
                    if (Math.abs(x - originX) + Math.abs(y - originY) != radius) continue;
                    if (isLegalActorCell(x, y, shared, occupied)) return new int[]{x, y};
                }
            }
        }
        return new int[]{shared.entryX, shared.entryY};
    }

    private static boolean isLegalActorCell(
            int x,
            int y,
            DungeonSharedFloor shared,
            List<DungeonActorContext> occupied
    ) {
        if (!shared.walkable(x, y) || shared.enemyOccupies(x, y)) return false;
        if (occupied != null) {
            for (DungeonActorContext actor : occupied) {
                if (actor.occupies(x, y)) return false;
            }
        }
        return true;
    }

    private void persistPeerDamage(DungeonTurnContext.Snapshot metadata) {
        if (metadata == null) return;
        for (DungeonActorContext peer : metadata.peers) {
            DungeonState raw = loadRaw(peer.npcId);
            if (raw == null || raw.floor != peer.floor || raw.hp == peer.hp) continue;
            raw.hp = Math.max(0, Math.min(raw.maxHp, peer.hp));
            saveRawInternal(peer.npcId, raw);
            archiveIfDead(peer.npcId, raw);
        }
    }

    private void archiveIfDead(String npcId, DungeonState state) {
        if (state != null && state.hp <= 0) {
            new NpcArchiveStore(appContext).archiveDeath(npcId, state);
        }
    }

    private void saveRawInternal(String npcId, DungeonState state) {
        preferences.edit().putString(key(npcId), state.toJson().toString()).commit();
    }

    private void registerState(
            DungeonState state,
            String npcId,
            long revision,
            List<DungeonActorContext> peers
    ) {
        DungeonTurnContext.register(
                state, npcId, revision, state.playerX, state.playerY, peers);
        synchronized (STATE_STORES) {
            STATE_STORES.put(state, this);
        }
    }

    private static DungeonActorContext actorById(List<DungeonActorContext> actors, String npcId) {
        if (actors == null) return null;
        for (DungeonActorContext actor : actors) {
            if (actor.npcId.equals(npcId)) return actor;
        }
        return null;
    }

    private static List<DungeonActorContext> peersExcept(
            List<DungeonActorContext> actors,
            String npcId
    ) {
        List<DungeonActorContext> peers = new ArrayList<>();
        if (actors == null) return peers;
        for (DungeonActorContext actor : actors) {
            if (!actor.npcId.equals(npcId)) peers.add(actor.copy());
        }
        return peers;
    }

    private static void copyVisited(boolean[][] source, boolean[][] target) {
        if (source == null || target == null) return;
        for (int y = 0; y < Math.min(source.length, target.length); y++) {
            if (source[y] == null || target[y] == null) continue;
            System.arraycopy(source[y], 0, target[y], 0,
                    Math.min(source[y].length, target[y].length));
        }
    }

    static String key(String npcId) {
        String id = NpcId.of(npcId).value();
        if ("npc1".equals(id)) return "npc1_state";
        if ("npc2".equals(id)) return "npc2_state";
        return id + "_state";
    }
}
