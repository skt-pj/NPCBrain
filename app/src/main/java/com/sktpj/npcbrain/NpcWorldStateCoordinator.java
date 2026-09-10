package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Single read entry for character, life, social and dungeon state of an NPC. */
final class NpcWorldStateCoordinator {
    private final Context appContext;
    private final WorldClock clock;
    private final WorldStateStore worldStore;
    private final NpcRegistryStore registry;
    private final DungeonPresenceStore dungeonPresence;
    private final DungeonStore dungeonStore;
    private final SocialRelationshipStore relationships;

    NpcWorldStateCoordinator(Context context) {
        appContext = context.getApplicationContext();
        clock = new WorldClock(appContext);
        worldStore = new WorldStateStore(appContext);
        registry = new NpcRegistryStore(appContext);
        dungeonPresence = new DungeonPresenceStore(appContext);
        dungeonStore = new DungeonStore(appContext);
        relationships = new SocialRelationshipStore(appContext);
    }

    NpcWorldSnapshot snapshot(String npcId) {
        return snapshot(npcId, 0L);
    }

    NpcWorldSnapshot snapshot(String npcId, long requestedWorldTimeMs) {
        String id = NpcId.of(npcId).value();
        // A snapshot is an observation. It must never advance the world clock just because a tab,
        // prompt builder, or diagnostic asks to read it. World progression belongs to runtimes.
        long worldTimeMs = clock.now();
        List<String> activeIds = registry.activeNpcIds();
        boolean active = activeIds.contains(id);
        Context storage = NpcContexts.storage(appContext, id);
        CharacterStateStore characterStore = new CharacterStateStore(storage);
        boolean dead = characterStore.isDead();

        JSONObject character = new JSONObject();
        if (!dead) {
            try {
                character = characterStore.snapshotJson();
            } catch (Exception ignored) {
            }
        }

        LifeState life = worldStore.lifeState(NpcId.of(id), worldTimeMs);
        JSONObject lifeJson = life == null ? new JSONObject() : life.toJson();
        JSONObject innerLife = copy(character.optJSONObject("inner_life"));

        JSONObject relationshipJson = new JSONObject();
        try {
            JSONArray items = relationships.contextFor(id, activeIds);
            relationshipJson.put("items", items);
        } catch (Exception ignored) {
        }

        boolean present = active && !dead && dungeonPresence.isPresent(id);
        DungeonState dungeonState = dungeonStore.loadRaw(id);
        JSONObject dungeonJson = dungeonState == null ? new JSONObject() : dungeonState.toJson();
        String effectiveLocation = life == null ? "unknown" : life.location();
        String effectiveActivity = life == null ? "idle" : life.currentActivity();
        if (present && dungeonState != null) {
            effectiveLocation = "dungeon_floor_" + dungeonState.floor;
            String action = dungeonState.lastAction == null ? "" : dungeonState.lastAction.trim();
            effectiveActivity = action.isEmpty() ? "dungeon_exploration" : action;
        }

        return new NpcWorldSnapshot(
                id,
                worldTimeMs,
                active,
                dead,
                character,
                lifeJson,
                innerLife,
                relationshipJson,
                present,
                dungeonJson,
                effectiveLocation,
                effectiveActivity);
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
