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
    private static final int MAX_EPISODES = 32;
    private static final int MAX_SEMANTICS = 24;
    private static final int RETRIEVED_EPISODES = 6;
    private static final int RETRIEVED_SEMANTICS = 8;

    private final SharedPreferences preferences;

    MemoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String contextFor(String query) {
        try {
            JSONArray episodes = loadArray(EPISODES);
            JSONArray semantics = loadArray(SEMANTICS);

            JSONObject context = new JSONObject();
            context.put("episodic_memory", selectEpisodes(episodes, query, RETRIEVED_EPISODES));
            context.put("semantic_memory", selectSemantics(semantics, query, RETRIEVED_SEMANTICS));
            context.put("episode_count", episodes.length());
            context.put("semantic_count", semantics.length());
            context.put("policy",
                    "Memory is retrieved by relevance, importance, and recency. Treat memory as fallible evidence, not current observation.");
            return context.toString();
        } catch (Exception ignored) {
            return "{\"episodic_memory\":[],\"semantic_memory\":[]}";
        }
    }

    synchronized Stats stats() {
        return new Stats(loadArray(EPISODES).length(), loadArray(SEMANTICS).length());
    }

    synchronized String preview() {
        JSONArray episodes = loadArray(EPISODES);
        JSONArray semantics = loadArray(SEMANTICS);
        StringBuilder text = new StringBuilder();

        text.append("エピソード記憶 ").append(episodes.length()).append("件\n");
        int episodeStart = Math.max(0, episodes.length() - 6);
        for (int i = episodes.length() - 1; i >= episodeStart; i--) {
            JSONObject item = episodes.optJSONObject(i);
            if (item == null) continue;
            String summary = item.optString("summary");
            if (summary.isEmpty()) summary = item.optString("input");
            text.append("・").append(limit(summary, 180)).append('\n');
        }

        text.append("\n意味記憶 ").append(semantics.length()).append("件\n");
        List<JSONObject> semanticItems = new ArrayList<>();
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item != null) semanticItems.add(item);
        }
        semanticItems.sort((a, b) -> Double.compare(b.optDouble("strength", 1.0), a.optDouble("strength", 1.0)));
        int count = Math.min(8, semanticItems.size());
        for (int i = 0; i < count; i++) {
            text.append("・").append(limit(semanticItems.get(i).optString("text"), 180)).append('\n');
        }

        if (episodes.length() == 0 && semantics.length() == 0) {
            return "まだ長期記憶はありません。思考を実行すると、重要な経験と安定した知識だけを保存します。";
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
            double clippedImportance = Math.max(0.0, Math.min(1.0, importance));
            JSONArray episodes = loadArray(EPISODES);
            JSONArray updatedEpisodes = new JSONArray();

            int start = Math.max(0, episodes.length() - (MAX_EPISODES - 1));
            for (int i = start; i < episodes.length(); i++) {
                updatedEpisodes.put(episodes.get(i));
            }

            JSONObject episode = new JSONObject();
            episode.put("time_ms", System.currentTimeMillis());
            episode.put("input", limit(input, 1800));
            episode.put("output", limit(output, 2200));
            episode.put("summary", limit(memorySummary, 900));
            episode.put("importance", clippedImportance);
            updatedEpisodes.put(episode);

            JSONArray semantics = loadArray(SEMANTICS);
            if (semanticFacts != null) {
                for (int i = 0; i < semanticFacts.length(); i++) {
                    String fact = semanticFacts.optString(i, "").trim();
                    if (!fact.isEmpty()) {
                        upsertSemantic(semantics, fact, Math.max(0.5, clippedImportance));
                    }
                }
            }
            if (clippedImportance >= 0.65 && memorySummary != null && !memorySummary.trim().isEmpty()) {
                upsertSemantic(semantics, memorySummary.trim(), clippedImportance);
            }

            semantics = trimSemantics(semantics);

            preferences.edit()
                    .putString(EPISODES, updatedEpisodes.toString())
                    .putString(SEMANTICS, semantics.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    synchronized void clear() {
        preferences.edit().remove(EPISODES).remove(SEMANTICS).apply();
    }

    private JSONArray selectEpisodes(JSONArray source, String query, int maxItems) {
        List<ScoredItem> scored = new ArrayList<>();
        int length = source.length();
        for (int i = 0; i < length; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String haystack = item.optString("input") + " " + item.optString("summary") + " " + item.optString("output");
            double relevance = similarity(query, haystack);
            double importance = item.optDouble("importance", 0.5);
            double recency = length <= 1 ? 1.0 : (double) i / (double) (length - 1);
            double score = relevance * 0.60 + importance * 0.25 + recency * 0.15;
            scored.add(new ScoredItem(item, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        JSONArray result = new JSONArray();
        for (int i = 0; i < Math.min(maxItems, scored.size()); i++) {
            result.put(scored.get(i).item);
        }
        return result;
    }

    private JSONArray selectSemantics(JSONArray source, String query, int maxItems) {
        List<ScoredItem> scored = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            double relevance = similarity(query, item.optString("text"));
            double strength = Math.min(1.0, item.optDouble("strength", 1.0) / 5.0);
            long last = item.optLong("last_ms", now);
            double ageDays = Math.max(0.0, (now - last) / 86_400_000.0);
            double recency = 1.0 / (1.0 + ageDays / 30.0);
            scored.add(new ScoredItem(item, relevance * 0.70 + strength * 0.20 + recency * 0.10));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        JSONArray result = new JSONArray();
        for (int i = 0; i < Math.min(maxItems, scored.size()); i++) {
            result.put(scored.get(i).item);
        }
        return result;
    }

    private void upsertSemantic(JSONArray semantics, String fact, double importance) {
        String normalizedFact = normalize(fact);
        int bestIndex = -1;
        double bestSimilarity = 0.0;

        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item == null) continue;
            String existing = item.optString("text");
            double score = similarity(normalizedFact, existing);
            if (score > bestSimilarity) {
                bestSimilarity = score;
                bestIndex = i;
            }
        }

        try {
            if (bestIndex >= 0 && bestSimilarity >= 0.72) {
                JSONObject existing = semantics.getJSONObject(bestIndex);
                existing.put("strength", Math.min(10.0, existing.optDouble("strength", 1.0) + 1.0 + importance));
                existing.put("last_ms", System.currentTimeMillis());
            } else {
                JSONObject item = new JSONObject();
                item.put("text", limit(fact, 900));
                item.put("strength", 1.0 + importance);
                item.put("last_ms", System.currentTimeMillis());
                semantics.put(item);
            }
        } catch (Exception ignored) {
        }
    }

    private JSONArray trimSemantics(JSONArray semantics) {
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < semantics.length(); i++) {
            JSONObject item = semantics.optJSONObject(i);
            if (item != null) items.add(item);
        }

        items.sort((a, b) -> {
            int strengthCompare = Double.compare(b.optDouble("strength", 1.0), a.optDouble("strength", 1.0));
            if (strengthCompare != 0) return strengthCompare;
            return Long.compare(b.optLong("last_ms", 0L), a.optLong("last_ms", 0L));
        });

        JSONArray result = new JSONArray();
        for (int i = 0; i < Math.min(MAX_SEMANTICS, items.size()); i++) {
            result.put(items.get(i));
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

    static double similarity(String left, String right) {
        Set<String> a = grams(normalize(left));
        Set<String> b = grams(normalize(right));
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int common = 0;
        for (String gram : a) {
            if (b.contains(gram)) common++;
        }
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
        for (int i = 0; i < value.length() - 1; i++) {
            result.add(value.substring(i, i + 2));
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}。、，．・！？「」『』（）【】]+", "");
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max);
    }
}
