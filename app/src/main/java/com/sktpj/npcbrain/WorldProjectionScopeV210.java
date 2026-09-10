package com.sktpj.npcbrain;

/** Marks derived projection writes so compatibility stores do not re-enter the world kernel. */
final class WorldProjectionScopeV210 implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    static WorldProjectionScopeV210 enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
        return new WorldProjectionScopeV210();
    }

    static boolean active() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    private WorldProjectionScopeV210() {}

    @Override
    public void close() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) DEPTH.remove();
        else DEPTH.set(depth - 1);
    }
}
