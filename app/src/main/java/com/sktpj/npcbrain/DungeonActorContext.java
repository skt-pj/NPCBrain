package com.sktpj.npcbrain;

/** Mutable per-NPC actor snapshot used while resolving one shared-dungeon turn. */
final class DungeonActorContext {
    final String npcId;
    int floor;
    int x;
    int y;
    int hp;
    final int maxHp;

    DungeonActorContext(String npcId, int floor, int x, int y, int hp, int maxHp) {
        this.npcId = NpcId.of(npcId).value();
        this.floor = Math.max(1, floor);
        this.x = x;
        this.y = y;
        this.maxHp = Math.max(1, maxHp);
        this.hp = Math.max(0, Math.min(this.maxHp, hp));
    }

    boolean alive() {
        return hp > 0;
    }

    boolean occupies(int px, int py) {
        return alive() && x == px && y == py;
    }

    DungeonActorContext copy() {
        return new DungeonActorContext(npcId, floor, x, y, hp, maxHp);
    }
}
