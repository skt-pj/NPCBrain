package com.sktpj.npcbrain;

final class DungeonItem {
    final String itemId;
    final String name;
    final long value;

    DungeonItem(String itemId, String name, long value) {
        this.itemId = itemId == null ? "item" : itemId;
        this.name = name == null ? "アイテム" : name;
        this.value = Math.max(0L, value);
    }
}
