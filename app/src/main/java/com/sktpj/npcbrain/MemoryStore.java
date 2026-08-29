package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MemoryStore {
    static final class Stats {
        final int episodes;
        final int semantics;

        Stats(int episodes, int semantics) {
            this.episodes = episodes;
            this.semantics = semantics;
        }
    }

    private static final class ScoredItem {
        final JSONObject item;
        final double score;

        ScoredItem(JSONObject item, double score) {
            this.item = item;
            this.score = score;
        }
    }

    private static final String PREFS = "npcbrain_memory_v2";
    private static final String EPISODES = "episodes";
    private static final String SEMANTICS = "semantics";
    private static final String SOURCE_PROFILE = "profile";
    private static final String SOURCE_LEARNED = "learned";
    private static final String STAGE_RECENT = "recent";
    private static final String STAGE_EPISODIC = "episodic";
    private static final int MAX_OLDER_EPISODES = 96;
    private static final int MAX_SEMANTICS = 48;
    private static final int RETRIEVED_EPISODES = 8;
    private static final int RETRIEVED_SEMANTICS = 10;

    private final SharedPreferences preferences;

    MemoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String contextFor(String query) {
        return contextFor(query, null);
    }

    synchronized String contextFor(String query, JSONObject characterState) {
        try {
            JSONArray episodes = loadArray(EPISODES);
            JSONArray semantics = loadArray(SEMANTICS);
            String characterContext = characterContext(semantics, characterState);
            JSONArray selectedEpisodes = selectEpisodes(
                    episodes, query, characterContext, RETRIEVED_EPISODES);
            JSONArray selectedSemantics = selectSemantics(
                    semantics, query, characterContext, RETRIEVED_SEMANTICS);
            long now = System.currentTimeMillis();
            boolean episodeTouched = touchRetrieved(episodes, selectedEpisodes, now);
            boolean semanticTouched = touchRetrieved(semantics, selectedSemantics, now);
            if (episodeTouched || semanticTouched) {
                SharedPreferences.Editor editor = preferences.edit();
                if (episodeTouched) editor.putString(EPISODES, episodes.toString());
                if (semanticTouched) editor.putString(SEMANTICS, semantics.toString());
                editor.apply();
            }

            JSONObject context = new JSONObject();
            context.put("recent_memory", filterStage(selectedEpisodes, STAGE_RECENT));
            context.put("episodic_memory", selectedEpisodes);
            context.put("semantic_memory", selectedSemantics);
            context.put("episode_count", episodes.length());
            context.put("semantic_count", semantics.length());
            context.put("policy",
                    "Memory is fallible evidence. Recent memories are provisional; consolidated episodes and learned semantics can later be compressed or forgotten. Retrieval can reinforce a memory. Profile adaptations are protected typed semantic memory.");
            return context.toString();
        } catch (Exception ignored) {
            return "{\"recent_memory\":[],\"episodic_memory\":[],\"semantic_memory\":[]}";
        }
    }

    synchronized JSONObject characterAdaptations() {
        JSONArray semantics = loadArray(SEMANTICS);
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < semantics.length(); i++) {
                JSONObject item = semantics.optJSONObject(i);
                if (item == null || !SOURCE_PROFILE.equals(item.optString("source"))) continue;
                String type = semanticType(item);
                String text = item.optString("text", "").trim();
                if (text.isEmpty()) continue;
                JSONArray values = result.optJSONArray(type);
                if (values == null) {
                    values = new JSONArray();
                    result.put(type, values);
                }
                values.put(text);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    synchronized String profileText(String type) {
        JSONArray semantics = loadArray(SEMANTICS);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item == null) continue;
            if (!SOURCE_PROFILE.equals(item.optString("source"))) continue;
            if (!normalizeType(type).equals(semanticType(item))) continue;
            String text = item.optString("text", "").trim();
            if (text.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(text);
        }
        return result.toString();
    }

    synchronized void replaceProfileAdaptations(
            String roleIdentity,
            String values,
            String goals,
            String fears,
            String relationships
    ) {
        JSONArray source = loadArray(SEMANTICS);
        JSONArray updated = new JSONArray();
        try {
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null) continue;
                if (!SOURCE_PROFILE.equals(item.optString("source"))) updated.put(item);
            }
            addProfileSemantic(updated, "role_identity", roleIdentity);
            addProfileSemantic(updated, "value", values);
            addProfileSemantic(updated, "goal", goals);
            addProfileSemantic(updated, "fear", fears);
            addProfileSemantic(updated, "relationship", relationships);
            preferences.edit().putString(SEMANTICS, trimSemantics(updated).toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized void clearProfileAdaptations() {
        JSONArray source = loadArray(SEMANTICS);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && !SOURCE_PROFILE.equals(item.optString("source"))) updated.put(item);
        }
        preferences.edit().putString(SEMANTICS, updated.toString()).apply();
    }

    synchronized Stats stats() {
        return new Stats(loadArray(EPISODES).length(), loadArray(SEMANTICS).length());
    }

    synchronized String preview() {
        JSONArray episodes = loadArray(EPISODES);
        JSONArray semantics = loadArray(SEMANTICS);
        StringBuilder text = new StringBuilder();
        int recent = 0;
        for (int i = 0; i < episodes.length(); i++) {
            if (STAGE_RECENT.equals(stageOf(episodes.optJSONObject(i)))) recent++;
        }
        text.append("最近の記憶候補 ").append(recent).append("件\n");
        text.append("エピソード記憶 ").append(Math.max(0, episodes.length() - recent)).append("件\n");
        int episodeStart = Math.max(0, episodes.length() - 8);
        for (int i = episodes.length() - 1; i >= episodeStart; i--) {
            JSONObject item = episodes.optJSONObject(i);
            if (item == null) continue;
            String summary = item.optString("summary");
            if (summary.isEmpty()) summary = item.optString("input");
            String marker = STAGE_RECENT.equals(stageOf(item)) ? "候補" : "固定";
            text.append("・[").append(marker).append("] ")
                    .append(limit(summary, 180)).append('\n');
        }

        text.append("\n意味記憶 ").append(semantics.length()).append("件\n");
        List<JSONObject> semanticItems = new ArrayList<>();
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item != null) semanticItems.add(item);
        }
        semanticItems.sort((a, b) -> {
            boolean profileA = SOURCE_PROFILE.equals(a.optString("source"));
            boolean profileB = SOURCE_PROFILE.equals(b.optString("source"));
            if (profileA != profileB) return profileA ? -1 : 1;
            return Double.compare(b.optDouble("strength", 1.0), a.optDouble("strength", 1.0));
        });
        int count = Math.min(12, semanticItems.size());
        for (int i = 0; i < count; i++) {
            JSONObject item = semanticItems.get(i);
            text.append("・[").append(typeLabel(semanticType(item))).append("] ")
                    .append(limit(item.optString("text"), 180)).append('\n');
        }

        if (episodes.length() == 0 && semantics.length() == 0) {
            return "まだ長期記憶はありません。思考後の記憶候補が定期的に固定・要約・忘却されます。";
        }
        return text.toString().trim();
    }

    synchronized void remember(
            String input,
            String output,
            String memorySummary,
            double importance,
            JSONArray semanticFacts
    ) {
        try {
            long now = System.currentTimeMillis();
            double clippedImportance = HumanMemoryPolicy.clamp01(importance);
            JSONArray episodes = loadArray(EPISODES);
            JSONObject episode = new JSONObject();
            episode.put("id", newMemoryId(now, episodes.length(), input, output));
            episode.put("time_ms", now);
            episode.put("input", limit(input, 1800));
            episode.put("output", limit(output, 2200));
            episode.put("summary", limit(memorySummary, 900));
            episode.put("importance", clippedImportance);
            episode.put("stage", STAGE_RECENT);
            episode.put("emotionality", 0.0);
            episode.put("social_relevance", 0.0);
            episode.put("repetition", 0.0);
            episode.put("retrieval_count", 0);
            episode.put("last_retrieved_ms", 0L);
            episode.put("semantic_candidates", copyArray(semanticFacts));
            episodes.put(episode);
            episodes = trimEpisodes(episodes, now);
            preferences.edit().putString(EPISODES, episodes.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized JSONArray maintenanceEpisodes() {
        return copyArray(loadArray(EPISODES));
    }

    synchronized JSONArray maintenanceSemantics() {
        return copyArray(loadArray(SEMANTICS));
    }

    synchronized void commitMaintenance(JSONArray episodes, JSONArray semantics) {
        long now = System.currentTimeMillis();
        preferences.edit()
                .putString(EPISODES, trimEpisodes(copyArray(episodes), now).toString())
                .putString(SEMANTICS, trimSemantics(copyArray(semantics)).toString())
                .commit();
    }

    synchronized void upsertLearnedSemantic(
            JSONArray semantics,
            String type,
            String fact,
            double importance
    ) {
        upsertSemantic(semantics, type, fact, importance, SOURCE_LEARNED);
    }

    synchronized void clear() {
        JSONArray semantics = loadArray(SEMANTICS);
        JSONArray profileOnly = new JSONArray();
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item != null && SOURCE_PROFILE.equals(item.optString("source"))) profileOnly.put(item);
        }
        preferences.edit()
                .remove(EPISODES)
                .putString(SEMANTICS, profileOnly.toString())
                .apply();
    }

    static String stageOf(JSONObject item) {
        if (item == null) return STAGE_EPISODIC;
        String stage = item.optString("stage", "").trim().toLowerCase(Locale.ROOT);
        return STAGE_RECENT.equals(stage) ? STAGE_RECENT : STAGE_EPISODIC;
    }

    static boolean isProfileSemantic(JSONObject item) {
        return item != null && SOURCE_PROFILE.equals(item.optString("source"));
    }

    static String normalizeSemanticType(String type) {
        return normalizeType(type);
    }

    private JSONArray selectEpisodes(
            JSONArray source,
            String query,
            String characterContext,
            int maxItems
    ) {
        List<ScoredItem> scored = new ArrayList<>();
        int length = source.length();
        for (int i = 0; i < length; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String haystack = item.optString("input") + " "
                    + item.optString("summary") + " "
                    + item.optString("output");
            double directRelevance = similarity(query, haystack);
            double characterRelevance = similarity(characterContext, haystack);
            double importance = item.optDouble("importance", 0.5);
            double recency = length <= 1 ? 1.0 : (double) i / (double) (length - 1);
            double recentStageBoost = STAGE_RECENT.equals(stageOf(item)) ? 0.04 : 0.0;
            double score = directRelevance * 0.48
                    + characterRelevance * 0.10
                    + importance * 0.24
                    + recency * 0.14
                    + recentStageBoost;
            scored.add(new ScoredItem(item, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        JSONArray result = new JSONArray();
        for (int i = 0; i < Math.min(maxItems, scored.size()); i++) result.put(scored.get(i).item);
        return result;
    }

    private JSONArray selectSemantics(
            JSONArray source,
            String query,
            String characterContext,
            int maxItems
    ) {
        List<ScoredItem> scored = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            double directRelevance = similarity(query, item.optString("text"));
            double characterRelevance = similarity(characterContext, item.optString("text"));
            double strength = Math.min(1.0, item.optDouble("strength", 1.0) / 5.0);
            long last = item.optLong("last_ms", now);
            double ageDays = Math.max(0.0, (now - last) / 86_400_000.0);
            double recency = 1.0 / (1.0 + ageDays / 30.0);
            double profileBoost = SOURCE_PROFILE.equals(item.optString("source")) ? 0.08 : 0.0;
            double score = directRelevance * 0.47
                    + characterRelevance * 0.15
                    + strength * 0.20
                    + recency * 0.10
                    + profileBoost;
            scored.add(new ScoredItem(item, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        JSONArray result = new JSONArray();
        for (int i = 0; i < Math.min(maxItems, scored.size()); i++) result.put(scored.get(i).item);
        return result;
    }

    private String characterContext(JSONArray semantics, JSONObject characterState) {
        StringBuilder result = new StringBuilder();
        if (characterState != null) {
            result.append(characterState.optString("name", "")).append(' ');
            result.append(characterState.optString("speech_style", "")).append(' ');
        }
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item == null || !SOURCE_PROFILE.equals(item.optString("source"))) continue;
            result.append(item.optString("text", "")).append(' ');
        }
        return result.toString();
    }

    private boolean touchRetrieved(JSONArray source, JSONArray selected, long now) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < selected.length(); i++) {
            JSONObject item = selected.optJSONObject(i);
            String id = ensureId(item, i);
            if (!id.isEmpty()) ids.add(id);
        }
        boolean changed = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String id = ensureId(item, i);
            if (!ids.contains(id)) continue;
            try {
                item.put("retrieval_count", Math.max(0, item.optInt("retrieval_count", 0)) + 1);
                item.put("last_retrieved_ms", now);
                changed = true;
            } catch (Exception ignored) {
            }
        }
        return changed;
    }

    private static String ensureId(JSONObject item, int fallbackIndex) {
        if (item == null) return "";
        String id = item.optString("id", "").trim();
        if (!id.isEmpty()) return id;
        long time = item.optLong("time_ms", item.optLong("last_ms", 0L));
        String basis = item.optString("summary", item.optString("text", item.optString("input", "")));
        id = "legacy_" + time + "_" + fallbackIndex + "_" + Integer.toHexString(basis.hashCode());
        try {
            item.put("id", id);
        } catch (Exception ignored) {
        }
        return id;
    }

    private static JSONArray filterStage(JSONArray source, String stage) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && stage.equals(stageOf(item))) result.put(item);
        }
        return result;
    }

    private void addProfileSemantic(JSONArray semantics, String type, String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        try {
            JSONObject item = new JSONObject();
            item.put("id", "profile_" + normalizeType(type) + "_" + Integer.toHexString(value.hashCode()));
            item.put("type", normalizeType(type));
            item.put("text", limit(value, 1200));
            item.put("strength", 10.0);
            item.put("last_ms", System.currentTimeMillis());
            item.put("source", SOURCE_PROFILE);
            item.put("retrieval_count", 0);
            item.put("last_retrieved_ms", 0L);
            semantics.put(item);
        } catch (Exception ignored) {
        }
    }

    private void upsertSemantic(
            JSONArray semantics,
            String type,
            String fact,
            double importance,
            String source
    ) {
        String normalizedType = normalizeType(type);
        String normalizedFact = normalize(fact);
        int bestIndex = -1;
        double bestSimilarity = 0.0;
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item == null) continue;
            if (!normalizedType.equals(semanticType(item))) continue;
            if (SOURCE_PROFILE.equals(item.optString("source"))) continue;
            double score = similarity(normalizedFact, item.optString("text"));
            if (score > bestSimilarity) {
                bestSimilarity = score;
                bestIndex = i;
            }
        }
        try {
            long now = System.currentTimeMillis();
            if (bestIndex >= 0 && bestSimilarity >= 0.72) {
                JSONObject existing = semantics.getJSONObject(bestIndex);
                existing.put("strength",
                        Math.min(10.0, existing.optDouble("strength", 1.0) + 1.0 + importance));
                existing.put("last_ms", now);
                existing.put("source", source);
                existing.put("type", normalizedType);
            } else {
                JSONObject item = new JSONObject();
                item.put("id", "sem_" + now + "_" + Integer.toHexString(fact.hashCode()));
                item.put("type", normalizedType);
                item.put("text", limit(fact, 900));
                item.put("strength", 1.0 + importance);
                item.put("last_ms", now);
                item.put("source", source);
                item.put("retrieval_count", 0);
                item.put("last_retrieved_ms", 0L);
                semantics.put(item);
            }
        } catch (Exception ignored) {
        }
    }

    private JSONArray trimEpisodes(JSONArray episodes, long now) {
        JSONArray recent = new JSONArray();
        List<JSONObject> older = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject item = episodes.optJSONObject(i);
            if (item == null) continue;
            ensureId(item, i);
            long age = Math.max(0L, now - item.optLong("time_ms", now));
            if (age < HumanMemoryPolicy.RECENT_DETAIL_PROTECTION_MS) recent.put(item);
            else older.add(item);
        }
        int start = Math.max(0, older.size() - MAX_OLDER_EPISODES);
        JSONArray result = new JSONArray();
        for (int i = start; i < older.size(); i++) result.put(older.get(i));
        for (int i = 0; i < recent.length(); i++) result.put(recent.opt(i));
        return result;
    }

    private JSONArray trimSemantics(JSONArray semantics) {
        List<JSONObject> profile = new ArrayList<>();
        List<JSONObject> learned = new ArrayList<>();
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item == null) continue;
            ensureId(item, i);
            if (SOURCE_PROFILE.equals(item.optString("source"))) profile.add(item);
            else learned.add(item);
        }
        learned.sort((a, b) -> {
            int strengthCompare = Double.compare(
                    b.optDouble("strength", 1.0), a.optDouble("strength", 1.0));
            if (strengthCompare != 0) return strengthCompare;
            return Long.compare(b.optLong("last_ms", 0L), a.optLong("last_ms", 0L));
        });
        JSONArray result = new JSONArray();
        for (JSONObject item : profile) result.put(item);
        for (JSONObject item : learned) {
            if (result.length() >= profile.size() + MAX_SEMANTICS) break;
            result.put(item);
        }
        return result;
    }

    private JSONArray loadArray(String key) {
        try {
            return new JSONArray(preferences.getString(key, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONArray copyArray(JSONArray source) {
        try {
            return source == null ? new JSONArray() : new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String semanticType(JSONObject item) {
        if (item == null) return "world_fact";
        return normalizeType(item.optString("type", "world_fact"));
    }

    private static String normalizeType(String type) {
        if (type == null) return "world_fact";
        String value = type.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "world_fact":
            case "self_belief":
            case "goal":
            case "value":
            case "fear":
            case "relationship":
            case "habit_strategy":
            case "role_identity":
                return value;
            default:
                return "world_fact";
        }
    }

    private static String typeLabel(String type) {
        switch (normalizeType(type)) {
            case "self_belief": return "自己像";
            case "goal": return "目標";
            case "value": return "価値";
            case "fear": return "恐れ";
            case "relationship": return "関係";
            case "habit_strategy": return "習慣";
            case "role_identity": return "役割";
            default: return "世界";
        }
    }

    static double similarity(String left, String right) {
        Set<String> a = grams(normalize(left));
        Set<String> b = grams(normalize(right));
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int common = 0;
        for (String gram : a) if (b.contains(gram)) common++;
        int union = a.size() + b.size() - common;
        return union == 0 ? 0.0 : (double) common / (double) union;
    }

    private static Set<String> grams(String value) {
        Set<String> result = new HashSet<>();
        if (value == null || value.isEmpty()) return result;
        if (value.length() == 1) {
            result.add(value);
            return result;
        }
        for (int i = 0; i < value.length() - 1; i++) result.add(value.substring(i, i + 2));
        return result;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}。、，．・！？「」『』（）【】]+", "");
    }

    private static String newMemoryId(long now, int ordinal, String input, String output) {
        String basis = (input == null ? "" : input) + "\n" + (output == null ? "" : output);
        return "mem_" + now + "_" + ordinal + "_" + Integer.toHexString(basis.hashCode());
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, max);
    }
}
