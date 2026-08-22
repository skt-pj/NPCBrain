package com.sktpj.npcbrain;

final class DungeonCombatEvent {
    static final String PLAYER_HIT = "player_hit";
    static final String ENEMY_DEFEATED = "enemy_defeated";
    static final String PLAYER_DAMAGED = "player_damaged";
    static final String FLOOR_CHANGED = "floor_changed";

    final String type;
    final int sourceX;
    final int sourceY;
    final int targetX;
    final int targetY;
    final int damage;
    final String targetId;

    DungeonCombatEvent(
            String type,
            int sourceX,
            int sourceY,
            int targetX,
            int targetY,
            int damage,
            String targetId
    ) {
        this.type = normalizeType(type);
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.damage = Math.max(0, damage);
        this.targetId = targetId == null ? "" : targetId;
    }

    boolean isCombatImpact() {
        return PLAYER_HIT.equals(type)
                || ENEMY_DEFEATED.equals(type)
                || PLAYER_DAMAGED.equals(type);
    }

    int impactTier() {
        if (PLAYER_DAMAGED.equals(type)) return 3;
        if (ENEMY_DEFEATED.equals(type)) return 2;
        if (PLAYER_HIT.equals(type)) return 1;
        return 0;
    }

    private static String normalizeType(String value) {
        if (PLAYER_HIT.equals(value)
                || ENEMY_DEFEATED.equals(value)
                || PLAYER_DAMAGED.equals(value)
                || FLOOR_CHANGED.equals(value)) {
            return value;
        }
        return "";
    }
}
