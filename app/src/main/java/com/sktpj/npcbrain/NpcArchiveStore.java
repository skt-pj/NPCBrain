package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class NpcArchiveStore {
    static final class Record {
        final String npcId;
        final long diedAtMs;
        final int floor;
        final int turn;
        final JSONObject personality;
        final JSONObject mindMap;

        Record(
                String npcId,
                long diedAtMs,
                int floor,
                int turn,
                JSONObject personality,
                JSONObject mindMap
        ) {
            this.npcId = safeNpcId(npcId);
            this.diedAtMs = Math.max(0L, diedAtMs);
            this.floor = Math.max(1, floor);
            this.turn = Math.max(0, turn);
            this.personality = copyObject(personality);
            this.mindMap = copyObject(mindMap);
        }

        String displayName() {
            String name = personality.optString("name", "NPC").trim();
            return name.isEmpty() ? "NPC" : name;
        }

        JSONObject traits() {
            return copyObject(personality.optJSONObject("traits"));
        }

        String speechStyle() {
            return personality.optString("speech_style", "").trim();
        }

        JSONObject toJson() {
            JSONObject root = new JSONObject();
            JSONObject battle = new JSONObject();
            try {
                battle.put("floor", floor);
                battle.put("turn", turn);
                root.put("npc_id", npcId);
                root.put("died_at_ms", diedAtMs);
                root.put("battle_record", battle);
                root.put("personality", personality);
                root.put("mind_map", mindMap);
            } catch (Exception ignored) {
            }
            return root;
        }

        static Record fromJson(JSONObject root) {
            if (root == null) return null;
            JSONObject battle = root.optJSONObject("battle_record");
            JSONObject personality = root.optJSONObject("personality");
            if (battle == null || personality == null) return null;
            return new Record(
                    root.optString("npc_id", "npc1"),
                    root.optLong("died_at_ms", 0L),
                    battle.optInt("floor", 1),
                    battle.optInt("turn", 0),
                    personality,
                    root.optJSONObject("mind_map"));
        }
    }

    private static final String PREFS = "npcbrain_archive_v1";
    private final Context appContext;
    private final SharedPreferences preferences;

    NpcArchiveStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean isDead(String npcId) {
        return load(npcId) != null;
    }

    synchronized Record load(String npcId) {
        String raw = preferences.getString(key(npcId), "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Record.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized List<Record> records() {
        List<Record> records = new ArrayList<>();
        Record npc1 = load("npc1");
        Record npc2 = load("npc2");
        if (npc1 != null) records.add(npc1);
        if (npc2 != null) records.add(npc2);
        return records;
    }

    synchronized Record archiveDeath(String npcId, DungeonState state) {
        if (state == null || state.hp > 0) return load(npcId);
        Record existing = load(npcId);
        if (existing != null) return existing;

        Context characterContext = characterContext(npcId);
        CharacterStateStore characterStore = new CharacterStateStore(characterContext);
        if (characterStore.isDead()) return null;

        JSONObject snapshot;
        try {
            snapshot = characterStore.snapshotJson();
        } catch (Exception ignored) {
            return null;
        }

        JSONObject personality = new JSONObject();
        try {
            personality.put("name", snapshot.optString("name", "NPC"));
            personality.put("speech_style", snapshot.optString("speech_style", ""));
            personality.put("traits", copyObject(snapshot.optJSONObject("traits")));
        } catch (Exception ignored) {
        }

        JSONObject mindMap = new JSONObject();
        DungeonMindStore mindStore = new DungeonMindStore(appContext);
        DungeonMindStore.Snapshot dungeonMind = mindStore.load(npcId);
        if (dungeonMind != null
                && CognitiveGraphBuilder.isValidSemanticSnapshot(dungeonMind.cognitiveGraph)) {
            mindMap = copyObject(dungeonMind.cognitiveGraph);
        } else {
            DemoCognitionObserver.Snapshot cognition = DemoCognitionObserver.snapshot(appContext, npcId);
            if (cognition != null
                    && CognitiveGraphBuilder.isValidSemanticSnapshot(cognition.cognitiveGraph)) {
                mindMap = copyObject(cognition.cognitiveGraph);
            }
        }

        Record record = new Record(
                npcId,
                System.currentTimeMillis(),
                state.floor,
                state.turn,
                personality,
                mindMap);
        boolean saved = preferences.edit()
                .putString(key(npcId), record.toJson().toString())
                .commit();
        if (!saved) return null;

        characterStore.markDead();
        mindStore.clear(npcId);
        return record;
    }

    private Context characterContext(String npcId) {
        return "npc2".equals(npcId)
                ? new NpcStorageContext(appContext, "npc2")
                : appContext;
    }

    static String key(String npcId) {
        return "npc2".equals(npcId) ? "npc2_archive" : "npc1_archive";
    }

    private static String safeNpcId(String npcId) {
        return "npc2".equals(npcId) ? "npc2" : "npc1";
    }

    private static JSONObject copyObject(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
