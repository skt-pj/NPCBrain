package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.util.UUID;

final class CharacterStateStore {
    static final String DEFAULT_RELATIONSHIP = "知人";
    static final String DEFAULT_AGE = "不明";
    static final String DEFAULT_OCCUPATION = "未設定";
    static final String DEFAULT_BACKGROUND = "特記事項なし";

    private static final String PREFS = "npcbrain_character_v1";
    private static final String NAME = "name";
    private static final String SPEECH_STYLE = "speech_style";
    private static final String EXTRAVERSION = "extraversion";
    private static final String NEUROTICISM = "neuroticism";
    private static final String AGREEABLENESS = "agreeableness";
    private static final String CONSCIENTIOUSNESS = "conscientiousness";
    private static final String OPENNESS = "openness";
    private static final String VALENCE = "valence";
    private static final String AROUSAL = "arousal";
    private static final String STRESS = "stress";
    private static final String RELATIONSHIP_TO_USER = "relationship_to_user";
    private static final String AGE = "age";
    private static final String OCCUPATION = "occupation";
    private static final String BACKGROUND = "background";
    private static final String IDENTITY_METADATA_LOCKED = "identity_metadata_locked";
    private static final String DEAD = "dead";

    private final Context storageContext;
    private final Context appContext;
    private final SharedPreferences preferences;
    private final String npcId;
    private long brainBasisStateVersion = -1L;

    CharacterStateStore(Context context) {
        storageContext = context;
        appContext = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        npcId = context instanceof NpcStorageContext
                ? ((NpcStorageContext) context).npcId()
                : "npc1";
    }

    synchronized boolean isDead() {
        CanonicalNpcStateV210 canonical = canonicalState();
        if (canonical != null && canonical.stateVersion() > 0L) return canonical.dead();
        return preferences.getBoolean(DEAD, false);
    }

    synchronized String displayName() {
        if (isDead()) return "死亡";
        String value = preferences.getString(NAME, "NPC");
        return value == null || value.trim().isEmpty() ? "NPC" : value.trim();
    }

    synchronized String speechStyle() {
        if (isDead()) return "";
        String value = preferences.getString(SPEECH_STYLE, "");
        return value == null ? "" : value;
    }

    synchronized int traitPercent(String key) {
        if (isDead()) return 0;
        return preferences.getInt(key, 50);
    }

    synchronized String relationshipToUser() {
        return safe(preferences.getString(RELATIONSHIP_TO_USER, DEFAULT_RELATIONSHIP), DEFAULT_RELATIONSHIP);
    }

    synchronized String age() {
        return safe(preferences.getString(AGE, DEFAULT_AGE), DEFAULT_AGE);
    }

    synchronized String occupation() {
        return safe(preferences.getString(OCCUPATION, DEFAULT_OCCUPATION), DEFAULT_OCCUPATION);
    }

    synchronized String background() {
        return safe(preferences.getString(BACKGROUND, DEFAULT_BACKGROUND), DEFAULT_BACKGROUND);
    }

    synchronized boolean identityMetadataLocked() {
        return preferences.getBoolean(IDENTITY_METADATA_LOCKED, false);
    }

    synchronized boolean initializeIdentityMetadata(
            String relationship,
            String age,
            String occupation,
            String background
    ) {
        if (isDead() || identityMetadataLocked()) return false;
        return preferences.edit()
                .putString(RELATIONSHIP_TO_USER, safe(relationship, DEFAULT_RELATIONSHIP))
                .putString(AGE, safe(age, DEFAULT_AGE))
                .putString(OCCUPATION, safe(occupation, DEFAULT_OCCUPATION))
                .putString(BACKGROUND, safe(background, DEFAULT_BACKGROUND))
                .putBoolean(IDENTITY_METADATA_LOCKED, true)
                .commit();
    }

    synchronized boolean updateIdentityMetadataForDebug(
            String relationship,
            String age,
            String occupation,
            String background
    ) {
        if (isDead()) return false;
        return preferences.edit()
                .putString(RELATIONSHIP_TO_USER, safe(relationship, DEFAULT_RELATIONSHIP))
                .putString(AGE, safe(age, DEFAULT_AGE))
                .putString(OCCUPATION, safe(occupation, DEFAULT_OCCUPATION))
                .putString(BACKGROUND, safe(background, DEFAULT_BACKGROUND))
                .putBoolean(IDENTITY_METADATA_LOCKED, true)
                .commit();
    }

    synchronized boolean initializeIdentityMetadata(String relationship, String age, String background) {
        return initializeIdentityMetadata(relationship, age, DEFAULT_OCCUPATION, background);
    }

    synchronized JSONObject snapshotJson() {
        if (isDead()) throw new IllegalStateException("NPC is dead");
        JSONObject root = new JSONObject();
        JSONObject traits = new JSONObject();
        JSONObject dynamic = new JSONObject();
        try {
            root.put("name", displayName());
            root.put("speech_style", speechStyle());
            root.put("relationship_to_user", relationshipToUser());
            root.put("age", age());
            root.put("occupation", occupation());
            root.put("background", background());

            JSONObject synthesis = new NpcProfileSynthesisStore(storageContext).load();
            if (synthesis.length() > 0) {
                root.put("profile_synthesis", new JSONObject(synthesis.toString()));
            }

            double extraversion = traitPercent(EXTRAVERSION) / 100.0;
            double neuroticism = traitPercent(NEUROTICISM) / 100.0;
            double agreeableness = traitPercent(AGREEABLENESS) / 100.0;
            double conscientiousness = traitPercent(CONSCIENTIOUSNESS) / 100.0;
            double openness = traitPercent(OPENNESS) / 100.0;
            traits.put(EXTRAVERSION, extraversion);
            traits.put(NEUROTICISM, neuroticism);
            traits.put(AGREEABLENESS, agreeableness);
            traits.put(CONSCIENTIOUSNESS, conscientiousness);
            traits.put(OPENNESS, openness);
            root.put("traits", traits);

            CanonicalNpcStateV210 canonical = canonicalState();
            if (canonical != null && canonical.stateVersion() > 0L) {
                brainBasisStateVersion = canonical.stateVersion();
                dynamic = canonical.dynamicState();
                if (dynamic.length() == 0) dynamic = legacyDynamicState();
                root.put("current_state", dynamic);
                JSONObject inner = canonical.innerLife();
                if (inner.length() == 0) {
                    inner = new NpcInnerLifeStore(storageContext).snapshotForBrain(
                            System.currentTimeMillis(), extraversion, neuroticism, openness);
                }
                root.put("inner_life", inner);
                root.put("world_runtime", canonical.toJson());
            } else {
                brainBasisStateVersion = -1L;
                dynamic = legacyDynamicState();
                root.put("current_state", dynamic);
                JSONObject innerLife = new NpcInnerLifeStore(storageContext).snapshotForBrain(
                        System.currentTimeMillis(), extraversion, neuroticism, openness);
                root.put("inner_life", innerLife);
            }

            root.put("economy", economySnapshot());
            root.put("neuroanatomy", NeuroanatomyModel.fullContext(root));
        } catch (Exception ignored) {
        }
        return root;
    }

    synchronized boolean saveProfile(
            String name,
            int extraversion,
            int neuroticism,
            int agreeableness,
            int conscientiousness,
            int openness,
            String speechStyle
    ) {
        if (isDead()) return false;
        boolean saved = preferences.edit()
                .putString(NAME, safe(name, "NPC"))
                .putInt(EXTRAVERSION, clampPercent(extraversion))
                .putInt(NEUROTICISM, clampPercent(neuroticism))
                .putInt(AGREEABLENESS, clampPercent(agreeableness))
                .putInt(CONSCIENTIOUSNESS, clampPercent(conscientiousness))
                .putInt(OPENNESS, clampPercent(openness))
                .putString(SPEECH_STYLE, speechStyle == null ? "" : speechStyle.trim())
                .commit();
        if (saved) new NpcProfileSynthesisStore(storageContext).clear();
        return saved;
    }

    synchronized void updateDynamicState(JSONObject state) {
        if (state == null || isDead()) return;
        JSONObject sanitized = sanitizeDynamic(state);
        if (!canonicalEnabled()) {
            writeLegacyDynamic(sanitized);
            return;
        }

        WorldKernelV210 kernel = WorldKernelV210.get(appContext);
        CanonicalNpcStateV210 current = canonicalState();
        long basis = brainBasisStateVersion >= 0L
                ? brainBasisStateVersion
                : current == null ? -1L : current.stateVersion();
        JSONObject payload = new JSONObject();
        try {
            payload.put("basis_state_version", basis);
            payload.put("dynamic_state", sanitized);
        } catch (Exception ignored) {
        }
        WorldCommitResultV210 commit = kernel.commit(WorldCommandV210.of(
                WorldCommandV210.APPLY_BRAIN_DECISION,
                System.currentTimeMillis(),
                npcId,
                "brain-state:" + npcId + ":" + UUID.randomUUID(),
                payload));
        if ("stale_brain_result".equals(commit.result.optString("status", ""))) {
            throw new IllegalStateException("Brain result is stale for " + npcId);
        }
        CanonicalNpcStateV210 after = canonicalState();
        brainBasisStateVersion = after == null ? -1L : after.stateVersion();
    }

    synchronized boolean resetDynamicState() {
        if (isDead()) return false;
        if (!canonicalEnabled()) {
            return preferences.edit()
                    .remove(VALENCE)
                    .remove(AROUSAL)
                    .remove(STRESS)
                    .commit();
        }
        JSONObject payload = new JSONObject();
        try { payload.put("dynamic_state", new JSONObject()); }
        catch (Exception ignored) {}
        WorldKernelV210.get(appContext).commit(WorldCommandV210.of(
                WorldCommandV210.UPSERT_DYNAMIC_STATE,
                System.currentTimeMillis(),
                npcId,
                "reset-dynamic:" + npcId + ":" + UUID.randomUUID(),
                payload));
        return true;
    }

    synchronized String dynamicStateSummary() {
        if (isDead()) return "—";
        JSONObject state = null;
        CanonicalNpcStateV210 canonical = canonicalState();
        if (canonical != null && canonical.stateVersion() > 0L) state = canonical.dynamicState();
        if (state == null || state.length() == 0) state = legacyDynamicState();
        int valence = (int) Math.round(clampSigned(state.optDouble("valence", 0.0)) * 100.0);
        int arousal = (int) Math.round(clamp01(state.optDouble("arousal", 0.25)) * 100.0);
        int stress = (int) Math.round(clamp01(state.optDouble("stress", 0.15)) * 100.0);
        return "感情価 " + signed(valence) + " · 覚醒 " + arousal + " · ストレス " + stress;
    }

    synchronized void markDead() {
        if (!canonicalEnabled() || WorldProjectionScopeV210.active()) {
            preferences.edit().clear().putBoolean(DEAD, true).commit();
            return;
        }
        WorldKernelV210.get(appContext).commit(WorldCommandV210.of(
                WorldCommandV210.MARK_NPC_DEAD,
                System.currentTimeMillis(),
                npcId,
                "death:" + npcId + ":" + UUID.randomUUID(),
                new JSONObject()));
    }

    synchronized void reset() {
        if (isDead()) return;
        boolean locked = identityMetadataLocked();
        String relationship = relationshipToUser();
        String age = age();
        String occupation = occupation();
        String background = background();
        new NpcProfileSynthesisStore(storageContext).clear();
        SharedPreferences.Editor editor = preferences.edit().clear();
        if (locked) {
            editor.putString(RELATIONSHIP_TO_USER, relationship)
                    .putString(AGE, age)
                    .putString(OCCUPATION, occupation)
                    .putString(BACKGROUND, background)
                    .putBoolean(IDENTITY_METADATA_LOCKED, true);
        }
        editor.apply();
    }

    static String extraversionKey() { return EXTRAVERSION; }
    static String neuroticismKey() { return NEUROTICISM; }
    static String agreeablenessKey() { return AGREEABLENESS; }
    static String conscientiousnessKey() { return CONSCIENTIOUSNESS; }
    static String opennessKey() { return OPENNESS; }

    private boolean canonicalEnabled() {
        if (WorldProjectionScopeV210.active()) return false;
        try {
            WorldDatabaseV210 db = WorldKernelV210.get(appContext).database();
            return "COMPLETED".equals(db.metaString(
                    db.getReadableDatabase(),
                    WorldDatabaseV210.META_MIGRATION_STATE,
                    "NOT_STARTED"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private CanonicalNpcStateV210 canonicalState() {
        if (!canonicalEnabled()) return null;
        try {
            WorldDatabaseV210 db = WorldKernelV210.get(appContext).database();
            SQLiteDatabase readable = db.getReadableDatabase();
            return db.loadNpc(readable, npcId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject legacyDynamicState() {
        JSONObject dynamic = new JSONObject();
        try {
            dynamic.put("valence", clampSigned(preferences.getFloat(VALENCE, 0.0f)));
            dynamic.put("arousal", clamp01(preferences.getFloat(AROUSAL, 0.25f)));
            dynamic.put("stress", clamp01(preferences.getFloat(STRESS, 0.15f)));
        } catch (Exception ignored) {
        }
        return dynamic;
    }

    private JSONObject sanitizeDynamic(JSONObject state) {
        JSONObject fallback = legacyDynamicState();
        JSONObject result = new JSONObject();
        try {
            result.put("valence", clampSigned(state.optDouble("valence", fallback.optDouble("valence", 0.0))));
            result.put("arousal", clamp01(state.optDouble("arousal", fallback.optDouble("arousal", 0.25))));
            result.put("stress", clamp01(state.optDouble("stress", fallback.optDouble("stress", 0.15))));
        } catch (Exception ignored) {
        }
        return result;
    }

    private void writeLegacyDynamic(JSONObject state) {
        float valence = (float) clampSigned(state.optDouble("valence", preferences.getFloat(VALENCE, 0.0f)));
        float arousal = (float) clamp01(state.optDouble("arousal", preferences.getFloat(AROUSAL, 0.25f)));
        float stress = (float) clamp01(state.optDouble("stress", preferences.getFloat(STRESS, 0.15f)));
        preferences.edit()
                .putFloat(VALENCE, valence)
                .putFloat(AROUSAL, arousal)
                .putFloat(STRESS, stress)
                .apply();
    }

    private JSONObject economySnapshot() {
        JSONObject economy = new JSONObject();
        try {
            NpcWalletStore wallet = new NpcWalletStore(storageContext);
            DungeonInventoryStore inventory = new DungeonInventoryStore(storageContext);
            DungeonItem latest = inventory.latest(npcId);
            economy.put("money", wallet.balance(npcId));
            economy.put("inventory_count", inventory.load(npcId).size());
            economy.put("inventory_appraised_value", inventory.totalAppraisedValue(npcId));
            if (latest != null) {
                economy.put("latest_item", new JSONObject()
                        .put("name", latest.name)
                        .put("value", latest.value)
                        .put("source", latest.source)
                        .put("floor", latest.floor));
            }
        } catch (Exception ignored) {
        }
        return economy;
    }

    private static int clampPercent(int value) { return Math.max(0, Math.min(100, value)); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static double clampSigned(double value) { return Math.max(-1.0, Math.min(1.0, value)); }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
