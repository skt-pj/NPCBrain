package com.sktpj.npcbrain;

import java.util.Locale;
import java.util.Objects;

final class NpcId {
    static final NpcId NPC1 = new NpcId("npc1");
    static final NpcId NPC2 = new NpcId("npc2");

    private final String value;

    private NpcId(String value) {
        this.value = value;
    }

    static NpcId of(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) throw new IllegalArgumentException("npc id must not be empty");
        if (NPC1.value.equals(normalized)) return NPC1;
        if (NPC2.value.equals(normalized)) return NPC2;
        return new NpcId(normalized);
    }

    String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NpcId)) return false;
        NpcId npcId = (NpcId) other;
        return value.equals(npcId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
