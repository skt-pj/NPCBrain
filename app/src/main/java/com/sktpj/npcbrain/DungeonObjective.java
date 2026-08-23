package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonObjective {
    static final String NONE = "none";
    static final String REACH_TOP = "reach_top";
    static final int TOP_FLOOR = 10;

    final String type;
    final int targetFloor;
    final long createdTimeMs;

    DungeonObjective(String type, int targetFloor, long createdTimeMs) {
        this.type = REACH_TOP.equals(type) ? REACH_TOP : NONE;
        this.targetFloor = REACH_TOP.equals(this.type) ? TOP_FLOOR : 0;
        this.createdTimeMs = Math.max(0L, createdTimeMs);
    }

    static DungeonObjective none() {
        return new DungeonObjective(NONE, 0, 0L);
    }

    static DungeonObjective reachTop(long createdTimeMs) {
        return new DungeonObjective(REACH_TOP, TOP_FLOOR, createdTimeMs);
    }

    boolean isActive() {
        return REACH_TOP.equals(type);
    }

    boolean isComplete(int floor) {
        return isActive() && floor >= targetFloor;
    }

    String label() {
        return isActive() ? "最上階へ到達 (" + targetFloor + "F)" : "未設定";
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("type", type);
            object.put("target_floor", targetFloor);
            object.put("created_time_ms", createdTimeMs);
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonObjective fromJson(JSONObject object) {
        if (object == null) return none();
        String type = object.optString("type", NONE);
        if (!REACH_TOP.equals(type)) return none();
        return new DungeonObjective(
                REACH_TOP,
                object.optInt("target_floor", TOP_FLOOR),
                object.optLong("created_time_ms", 0L));
    }
}
