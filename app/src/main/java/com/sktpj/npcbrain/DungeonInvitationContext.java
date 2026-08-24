package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonInvitationContext {
    static final String LOW = "low";
    static final String GUARDED = "guarded";
    static final String HIGH = "high";
    static final String CRITICAL = "critical";

    final String npcId;
    final long invitedAtMs;
    final String destinationLabel;
    final int floor;
    final int turn;
    final int hp;
    final int maxHp;
    final String objectiveLabel;
    final int visibleEnemyCount;
    final int nearestVisibleEnemyDistance;
    final String dangerBand;

    DungeonInvitationContext(
            String npcId,
            long invitedAtMs,
            String destinationLabel,
            int floor,
            int turn,
            int hp,
            int maxHp,
            String objectiveLabel,
            int visibleEnemyCount,
            int nearestVisibleEnemyDistance,
            String dangerBand
    ) {
        this.npcId = normalizeNpcId(npcId);
        this.invitedAtMs = Math.max(0L, invitedAtMs);
        this.floor = Math.max(1, floor);
        this.destinationLabel = clean(destinationLabel).isEmpty()
                ? "ダンジョン " + this.floor + "F"
                : clean(destinationLabel);
        this.turn = Math.max(0, turn);
        this.maxHp = Math.max(1, maxHp);
        this.hp = Math.max(0, Math.min(this.maxHp, hp));
        this.objectiveLabel = clean(objectiveLabel);
        this.visibleEnemyCount = Math.max(0, visibleEnemyCount);
        this.nearestVisibleEnemyDistance = nearestVisibleEnemyDistance < 0
                ? 999 : nearestVisibleEnemyDistance;
        this.dangerBand = normalizeDanger(dangerBand);
    }

    static DungeonInvitationContext fromDungeon(
            String npcId,
            DungeonState state,
            DungeonObjective objective,
            long invitedAtMs
    ) {
        if (state == null) return null;
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        int visibleCount = DungeonPerception.visibleEnemyIds(state).size();
        int nearest = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        return new DungeonInvitationContext(
                npcId,
                invitedAtMs,
                "ダンジョン " + state.floor + "F",
                state.floor,
                state.turn,
                state.hp,
                state.maxHp,
                goal.label(),
                visibleCount,
                nearest,
                dangerBand(state.hp, state.maxHp, visibleCount, nearest));
    }

    static String dangerBand(int hp, int maxHp, int visibleEnemyCount, int nearestDistance) {
        int safeMax = Math.max(1, maxHp);
        double hpRatio = Math.max(0.0, Math.min(1.0, hp / (double) safeMax));
        int count = Math.max(0, visibleEnemyCount);
        int nearest = nearestDistance < 0 ? 999 : nearestDistance;
        if (hpRatio <= 0.20 && nearest <= 2) return CRITICAL;
        if (hpRatio <= 0.35 || nearest <= 1) return HIGH;
        if (hpRatio <= 0.60 || nearest <= 3 || count >= 2) return GUARDED;
        return LOW;
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("source", "dungeon_invitation");
            object.put("npc_id", npcId);
            object.put("invited_at_ms", invitedAtMs);
            object.put("destination_label", destinationLabel);
            object.put("floor", floor);
            object.put("turn", turn);
            object.put("hp", hp);
            object.put("max_hp", maxHp);
            object.put("objective", objectiveLabel);
            object.put("visible_enemy_count", visibleEnemyCount);
            if (nearestVisibleEnemyDistance < 999) {
                object.put("nearest_visible_enemy_distance", nearestVisibleEnemyDistance);
            }
            object.put("danger_band", dangerBand);
            object.put("knowledge_scope", "visible_or_explicit_dungeon_state_only");
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonInvitationContext fromJson(JSONObject object) {
        if (object == null || object.length() == 0) return null;
        String npcId = object.optString("npc_id", "");
        if (npcId.trim().isEmpty()) return null;
        int floor = object.optInt("floor", 1);
        int nearest = object.has("nearest_visible_enemy_distance")
                ? object.optInt("nearest_visible_enemy_distance", 999) : 999;
        return new DungeonInvitationContext(
                npcId,
                object.optLong("invited_at_ms", 0L),
                object.optString("destination_label", ""),
                floor,
                object.optInt("turn", 0),
                object.optInt("hp", 0),
                object.optInt("max_hp", 1),
                object.optString("objective", ""),
                object.optInt("visible_enemy_count", 0),
                nearest,
                object.optString("danger_band", LOW));
    }

    private static String normalizeNpcId(String value) {
        try {
            return NpcId.of(value).value();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeDanger(String value) {
        String text = clean(value).toLowerCase(java.util.Locale.US);
        if (CRITICAL.equals(text) || HIGH.equals(text) || GUARDED.equals(text)) return text;
        return LOW;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }
}
