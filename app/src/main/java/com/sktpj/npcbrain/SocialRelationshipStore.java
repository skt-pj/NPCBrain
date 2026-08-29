package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

final class SocialRelationshipStore {
    private static final String PREFS = "npcbrain_social_relationships_v1";
    private final SharedPreferences preferences;

    SocialRelationshipStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized JSONObject get(String subjectNpcId, String otherNpcId) {
        String subject = NpcId.of(subjectNpcId).value();
        String other = NpcId.of(otherNpcId).value();
        try {
            String raw = preferences.getString(key(subject, other), "");
            if (raw != null && !raw.trim().isEmpty()) return new JSONObject(raw);
        } catch (Exception ignored) {
        }
        return empty(subject, other);
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
        JSONObject current = get(subject, other);
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
            long last = Math.max(current.optLong("last_interaction_ms", 0L), Math.max(0L, lastInteractionMs));
            String nextSummary = summary == null ? "" : summary.trim();
            if (nextSummary.isEmpty()) nextSummary = current.optString("summary", "");

            JSONObject updated = new JSONObject();
            updated.put("subject_id", subject);
            updated.put("other_id", other);
            updated.put("familiarity", familiarity);
            updated.put("trust", trust);
            updated.put("affinity", affinity);
            updated.put("summary", nextSummary);
            updated.put("interaction_count", interactions);
            updated.put("last_interaction_ms", last);
            updated.put("updated_ms", Math.max(0L, nowMs));
            preferences.edit().putString(key(subject, other), updated.toString()).commit();
            return new JSONObject(updated.toString());
        } catch (Exception ignored) {
            return current;
        }
    }

    synchronized void clearLearnedFor(String subjectNpcId) {
        String prefix = "rel_" + safe(NpcId.of(subjectNpcId).value()) + "__";
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
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

    private static String key(String subject, String other) {
        return "rel_" + safe(subject) + "__" + safe(other);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US)
                .replaceAll("[^a-z0-9_-]", "_");
    }
}
