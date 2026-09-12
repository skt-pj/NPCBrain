package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Relationship runtime reads canonical state; SharedPreferences is a replayable compatibility projection. */
final class SocialRelationshipStore {
    private static final String PREFS = "npcbrain_social_relationships_v1";
    private final Context appContext;
    private final SharedPreferences preferences;

    SocialRelationshipStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized JSONObject get(String subjectNpcId, String otherNpcId) {
        String subject = NpcId.of(subjectNpcId).value();
        String other = NpcId.of(otherNpcId).value();
        if (!WorldProjectionScopeV210.active() && canonicalReady()) {
            JSONObject canonical = canonicalPair(subject, other);
            if (canonical.length() > 0) return canonical;
        }
        return legacyGet(subject, other);
    }

    synchronized JSONArray contextFor(String subjectNpcId, List<String> activeNpcIds) {
        JSONArray result = new JSONArray();
        String subject = NpcId.of(subjectNpcId).value();
        if (activeNpcIds == null) return result;
        for (String raw : activeNpcIds) {
            try {
                String other = NpcId.of(raw).value();
                if (!subject.equals(other)) result.put(get(subject, other));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    synchronized JSONObject applyUpdate(
            String subjectNpcId,
            String otherNpcId,
            double familiarityDelta,
            double trustDelta,
            double affinityDelta,
            String summary,
            int interactionIncrement,
            long lastInteractionMs,
            long nowMs
    ) {
        String subject = NpcId.of(subjectNpcId).value();
        String other = NpcId.of(otherNpcId).value();
        if (subject.equals(other)) return empty(subject, other);
        if (WorldProjectionScopeV210.active()) {
            return applyLegacyUpdate(
                    subject, other, familiarityDelta, trustDelta, affinityDelta,
                    summary, interactionIncrement, lastInteractionMs, nowMs);
        }
        if (!ensureCanonicalReady()) return legacyGet(subject, other);

        JSONObject current = canonicalPair(subject, other);
        if (current.length() == 0) current = empty(subject, other);
        long currentLastInteraction = Math.max(0L, current.optLong("last_interaction_ms", 0L));
        long evidenceLastInteraction = Math.max(0L, lastInteractionMs);
        // A relationship mutation must be grounded in a committed interaction/evidence boundary.
        if (evidenceLastInteraction <= 0L || evidenceLastInteraction <= currentLastInteraction) {
            return current;
        }

        JSONObject updated = updatedRelationship(
                current,
                subject,
                other,
                familiarityDelta,
                trustDelta,
                affinityDelta,
                summary,
                interactionIncrement,
                evidenceLastInteraction,
                nowMs);
        JSONObject relationships = canonicalRelationships(subject);
        replacePair(relationships, updated);

        JSONObject payload = new JSONObject();
        try {
            payload.put("relationships", relationships);
            payload.put("relationship", new JSONObject(updated.toString()));
            payload.put("other_id", other);
            payload.put("participant_ids", new JSONArray().put(subject).put(other));
            payload.put("evidence_last_interaction_ms", evidenceLastInteraction);
            payload.put("evidence_interaction_count", Math.max(0, interactionIncrement));
            payload.put("observed_wall_time_ms", Math.max(0L, nowMs));
        } catch (Exception ignored) {
        }
        String idempotency = "relationship:" + subject + ":" + other + ":" + evidenceLastInteraction;
        WorldKernelV210.get(appContext).commit(WorldCommandV210.of(
                WorldCommandV210.APPLY_RELATIONSHIP,
                Math.max(0L, nowMs),
                subject,
                idempotency,
                payload));
        JSONObject committed = canonicalPair(subject, other);
        return committed.length() == 0 ? updated : committed;
    }

    synchronized void clearLearnedFor(String subjectNpcId) {
        String subject = NpcId.of(subjectNpcId).value();
        if (WorldProjectionScopeV210.active()) {
            clearLegacy(subject);
            return;
        }
        if (!ensureCanonicalReady()) return;
        JSONObject relationships = new JSONObject();
        try { relationships.put("items", new JSONArray()); } catch (Exception ignored) {}
        JSONObject payload = new JSONObject();
        try {
            payload.put("relationships", relationships);
            payload.put("participant_ids", new JSONArray().put(subject));
            payload.put("reset", true);
            payload.put("observed_wall_time_ms", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        WorldKernelV210.get(appContext).commit(WorldCommandV210.of(
                WorldCommandV210.APPLY_RELATIONSHIP,
                System.currentTimeMillis(),
                subject,
                "relationship-reset:" + subject + ":" + System.currentTimeMillis(),
                payload));
    }

    /** Compatibility projection entry. Must only be called from a projection scope. */
    synchronized void projectCanonical(JSONObject relationship) {
        if (!WorldProjectionScopeV210.active() || relationship == null) return;
        String subject;
        String other;
        try {
            subject = NpcId.of(relationship.optString("subject_id", "")).value();
            other = NpcId.of(relationship.optString("other_id", "")).value();
        } catch (Exception invalid) {
            return;
        }
        if (subject.equals(other)) return;
        try {
            preferences.edit().putString(
                    key(subject, other),
                    new JSONObject(relationship.toString()).toString()).commit();
        } catch (Exception ignored) {
        }
    }

    private boolean ensureCanonicalReady() {
        if (canonicalReady()) return true;
        try {
            WorldKernelV210 kernel = WorldKernelV210.get(appContext);
            new LegacyWorldImporterV210(appContext, kernel.database()).importIfNeeded();
            return canonicalReady();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canonicalReady() {
        try {
            WorldKernelV210 kernel = WorldKernelV210.get(appContext);
            return "COMPLETED".equals(kernel.database().metaString(
                    kernel.database().getReadableDatabase(),
                    WorldDatabaseV210.META_MIGRATION_STATE,
                    "NOT_STARTED"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private JSONObject canonicalRelationships(String subject) {
        try {
            JSONObject snapshot = new WorldQueryServiceV210(
                    WorldKernelV210.get(appContext).database()).snapshot(subject);
            JSONObject npc = snapshot.optJSONObject("npc");
            JSONObject relationships = copy(npc == null ? null : npc.optJSONObject("relationships"));
            if (relationships.optJSONArray("items") == null) relationships.put("items", new JSONArray());
            return relationships;
        } catch (Exception ignored) {
            JSONObject empty = new JSONObject();
            try { empty.put("items", new JSONArray()); } catch (Exception ignoredAgain) {}
            return empty;
        }
    }

    private JSONObject canonicalPair(String subject, String other) {
        JSONArray items = canonicalRelationships(subject).optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (subject.equals(item.optString("subject_id", ""))
                    && other.equals(item.optString("other_id", ""))) {
                return copy(item);
            }
        }
        return new JSONObject();
    }

    private static void replacePair(JSONObject relationships, JSONObject updated) {
        JSONArray source = relationships.optJSONArray("items");
        JSONArray next = new JSONArray();
        String subject = updated.optString("subject_id", "");
        String other = updated.optString("other_id", "");
        boolean replaced = false;
        for (int i = 0; source != null && i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            if (subject.equals(item.optString("subject_id", ""))
                    && other.equals(item.optString("other_id", ""))) {
                next.put(copy(updated));
                replaced = true;
            } else {
                next.put(copy(item));
            }
        }
        if (!replaced) next.put(copy(updated));
        try { relationships.put("items", next); } catch (Exception ignored) {}
    }

    private JSONObject legacyGet(String subject, String other) {
        try {
            String raw = preferences.getString(key(subject, other), "");
            if (raw != null && !raw.trim().isEmpty()) return new JSONObject(raw);
        } catch (Exception ignored) {
        }
        return empty(subject, other);
    }

    private JSONObject applyLegacyUpdate(
            String subject,
            String other,
            double familiarityDelta,
            double trustDelta,
            double affinityDelta,
            String summary,
            int interactionIncrement,
            long lastInteractionMs,
            long nowMs
    ) {
        JSONObject current = legacyGet(subject, other);
        long currentLastInteraction = Math.max(0L, current.optLong("last_interaction_ms", 0L));
        long evidenceLastInteraction = Math.max(0L, lastInteractionMs);
        if (evidenceLastInteraction > 0L && evidenceLastInteraction <= currentLastInteraction) {
            return current;
        }
        JSONObject updated = updatedRelationship(
                current,
                subject,
                other,
                familiarityDelta,
                trustDelta,
                affinityDelta,
                summary,
                interactionIncrement,
                evidenceLastInteraction,
                nowMs);
        try {
            preferences.edit().putString(key(subject, other), updated.toString()).commit();
            return new JSONObject(updated.toString());
        } catch (Exception ignored) {
            return current;
        }
    }

    private static JSONObject updatedRelationship(
            JSONObject current,
            String subject,
            String other,
            double familiarityDelta,
            double trustDelta,
            double affinityDelta,
            String summary,
            int interactionIncrement,
            long evidenceLastInteraction,
            long nowMs
    ) {
        JSONObject updated = new JSONObject();
        try {
            double familiarity = HumanMemoryPolicy.clamp01(
                    current.optDouble("familiarity", 0.0)
                            + HumanMemoryPolicy.clampRelationshipDelta(familiarityDelta));
            double trust = HumanMemoryPolicy.clampSigned(
                    current.optDouble("trust", 0.0)
                            + HumanMemoryPolicy.clampRelationshipDelta(trustDelta));
            double affinity = HumanMemoryPolicy.clampSigned(
                    current.optDouble("affinity", 0.0)
                            + HumanMemoryPolicy.clampRelationshipDelta(affinityDelta));
            int interactions = Math.max(0, current.optInt("interaction_count", 0))
                    + Math.max(0, interactionIncrement);
            long last = Math.max(
                    Math.max(0L, current.optLong("last_interaction_ms", 0L)),
                    Math.max(0L, evidenceLastInteraction));
            String nextSummary = summary == null ? "" : summary.trim();
            if (nextSummary.isEmpty()) nextSummary = current.optString("summary", "");

            updated.put("subject_id", subject);
            updated.put("other_id", other);
            updated.put("familiarity", familiarity);
            updated.put("trust", trust);
            updated.put("affinity", affinity);
            updated.put("summary", nextSummary);
            updated.put("interaction_count", interactions);
            updated.put("last_interaction_ms", last);
            updated.put("updated_ms", Math.max(0L, nowMs));
        } catch (Exception ignored) {
        }
        return updated;
    }

    private void clearLegacy(String subject) {
        String prefix = "rel_" + safe(subject) + "__";
        SharedPreferences.Editor editor = preferences.edit();
        for (String storedKey : preferences.getAll().keySet()) {
            if (storedKey.startsWith(prefix)) editor.remove(storedKey);
        }
        editor.commit();
    }

    private static JSONObject empty(String subject, String other) {
        JSONObject json = new JSONObject();
        try {
            json.put("subject_id", subject);
            json.put("other_id", other);
            json.put("familiarity", 0.0);
            json.put("trust", 0.0);
            json.put("affinity", 0.0);
            json.put("summary", "");
            json.put("interaction_count", 0);
            json.put("last_interaction_ms", 0L);
            json.put("updated_ms", 0L);
        } catch (Exception ignored) {
        }
        return json;
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String key(String subject, String other) {
        return "rel_" + safe(subject) + "__" + safe(other);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US)
                .replaceAll("[^a-z0-9_-]", "_");
    }
}
