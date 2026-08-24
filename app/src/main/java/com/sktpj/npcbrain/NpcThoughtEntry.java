package com.sktpj.npcbrain;

import org.json.JSONObject;

final class NpcThoughtEntry {
    static final String SOURCE_LOCAL = "local";
    static final String SOURCE_AMBIENT = "ambient";
    static final String SOURCE_REFLECTION = "reflection";

    final long timeMs;
    final String source;
    final String text;

    NpcThoughtEntry(long timeMs, String source, String text) {
        this.timeMs = Math.max(0L, timeMs);
        this.source = normalizeSource(source);
        this.text = limit(text, 500);
    }

    static NpcThoughtEntry fromJson(JSONObject json) {
        if (json == null) return null;
        String text = limit(json.optString("text", ""), 500);
        if (text.isEmpty()) return null;
        return new NpcThoughtEntry(
                json.optLong("time_ms", 0L),
                json.optString("source", SOURCE_LOCAL),
                text
        );
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("time_ms", timeMs);
            json.put("source", source);
            json.put("text", text);
        } catch (Exception ignored) {
        }
        return json;
    }

    static String normalizeSource(String source) {
        if (SOURCE_AMBIENT.equals(source)) return SOURCE_AMBIENT;
        if (SOURCE_REFLECTION.equals(source)) return SOURCE_REFLECTION;
        return SOURCE_LOCAL;
    }

    static String limit(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= max) return text;
        return text.substring(0, max);
    }
}
