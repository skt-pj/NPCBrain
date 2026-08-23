package com.sktpj.npcbrain;

import android.content.Context;

/** Compatibility wrapper. NpcAiStaminaStore is the single source of truth for all NPC AI usage. */
final class DungeonAiStaminaStore {
    static final class Snapshot {
        final double spentJpy;
        final double remainingJpy;
        final int remainingPercent;
        final long inputTokens;
        final long cachedInputTokens;
        final long outputTokens;
        final long totalTokens;

        Snapshot(NpcAiStaminaStore.Snapshot source) {
            this.spentJpy = source.spentJpy;
            this.remainingJpy = source.remainingJpy;
            this.remainingPercent = source.remainingPercent;
            this.inputTokens = source.inputTokens;
            this.cachedInputTokens = source.cachedInputTokens;
            this.outputTokens = source.outputTokens;
            this.totalTokens = source.totalTokens;
        }

        boolean exhausted() {
            return remainingJpy <= 0.000001;
        }
    }

    private final NpcAiStaminaStore delegate;

    DungeonAiStaminaStore(Context context) {
        delegate = new NpcAiStaminaStore(context);
    }

    synchronized Snapshot snapshot(String npcId) {
        return new Snapshot(delegate.snapshot(npcId));
    }

    synchronized Snapshot recordUsage(String npcId, OpenAiClient.Usage usage) {
        return new Snapshot(delegate.recordUsage(npcId, usage));
    }
}