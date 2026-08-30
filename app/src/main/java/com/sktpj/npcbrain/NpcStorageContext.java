package com.sktpj.npcbrain;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

final class NpcStorageContext extends ContextWrapper {
    private final String suffix;

    NpcStorageContext(Context base, String namespace) {
        super(base.getApplicationContext());
        this.suffix = sanitize(namespace);
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(name + "_" + suffix, mode);
    }

    String npcId() {
        return suffix;
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) return "npc";
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
