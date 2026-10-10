package com.sktpj.npcbrain;

import org.json.JSONObject;

/**
 * Binds one immutable canonical world snapshot to a single BrainEngine invocation.
 * Character state reads performed during the invocation must resolve from this snapshot so the
 * brain never mixes multiple canonical revisions in one decision cycle.
 */
final class BrainContextScopeV210 {
    private static final ThreadLocal<FrozenContext> CURRENT = new ThreadLocal<>();

    private BrainContextScopeV210() {
    }

    static Scope enter(String npcId, JSONObject worldSnapshot) {
        FrozenContext previous = CURRENT.get();
        CURRENT.set(new FrozenContext(NpcId.of(npcId).value(), copy(worldSnapshot)));
        return new Scope(previous);
    }

    static JSONObject frozenNpc(String npcId) {
        FrozenContext current = CURRENT.get();
        if (current == null || !current.npcId.equals(NpcId.of(npcId).value())) return null;
        return copy(current.worldSnapshot.optJSONObject("npc"));
    }

    static long basisRevision(String npcId) {
        FrozenContext current = CURRENT.get();
        if (current == null || !current.npcId.equals(NpcId.of(npcId).value())) return -1L;
        return current.worldSnapshot.optLong("revision", -1L);
    }

    static final class Scope implements AutoCloseable {
        private final FrozenContext previous;
        private boolean closed;

        private Scope(FrozenContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private static final class FrozenContext {
        final String npcId;
        final JSONObject worldSnapshot;

        FrozenContext(String npcId, JSONObject worldSnapshot) {
            this.npcId = npcId;
            this.worldSnapshot = worldSnapshot;
        }
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
