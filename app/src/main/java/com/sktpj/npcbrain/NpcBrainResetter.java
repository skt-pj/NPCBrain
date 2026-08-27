package com.sktpj.npcbrain;

import android.content.Context;

final class NpcBrainResetter {
    private NpcBrainResetter() {
    }

    static boolean reset(Context context, String npcId) {
        if (context == null) return false;
        Context appContext = context.getApplicationContext();
        String id;
        try {
            id = NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return false;
        }

        NpcRegistryStore registry = new NpcRegistryStore(appContext);
        if (!registry.contains(id)) return false;

        Context storageContext = NpcContexts.storage(appContext, id);
        CharacterStateStore characterStore = new CharacterStateStore(storageContext);
        if (characterStore.isDead()) return false;

        new MemoryStore(storageContext).clear();
        characterStore.resetDynamicState();
        new NpcInnerLifeStore(storageContext).clear();
        new DungeonMindStore(appContext).clear(id);
        new ReplyTimerStore(appContext).clearForNpc(id);
        return true;
    }
}
