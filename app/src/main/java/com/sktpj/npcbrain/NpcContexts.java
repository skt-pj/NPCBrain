package com.sktpj.npcbrain;

import android.content.Context;

final class NpcContexts {
    private NpcContexts() {
    }

    static Context storage(Context context, String npcId) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Context app = context.getApplicationContext();
        String id = NpcId.of(npcId).value();
        return "npc1".equals(id) ? app : new NpcStorageContext(app, id);
    }
}
