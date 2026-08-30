package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class NpcWalletStore {
    private static final String PREFS = "npcbrain_wallet_v042";
    private final SharedPreferences preferences;

    NpcWalletStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized long balance(String npcId) {
        String id = NpcId.of(npcId).value();
        return Math.max(0L, preferences.getLong(key(id), 0L));
    }

    synchronized long credit(String npcId, long amount) {
        String id = NpcId.of(npcId).value();
        long current = balance(id);
        long add = Math.max(0L, amount);
        long next = Long.MAX_VALUE - current < add ? Long.MAX_VALUE : current + add;
        preferences.edit().putLong(key(id), next).commit();
        return next;
    }

    synchronized boolean debit(String npcId, long amount) {
        String id = NpcId.of(npcId).value();
        long cost = Math.max(0L, amount);
        long current = balance(id);
        if (cost > current) return false;
        preferences.edit().putLong(key(id), current - cost).commit();
        return true;
    }

    private static String key(String npcId) {
        return "money_" + npcId;
    }
}
