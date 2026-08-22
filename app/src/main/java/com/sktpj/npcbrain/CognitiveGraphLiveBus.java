package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Read-only observation path for the actual CognitiveWorkingGraph.
 * This class is never read by cognition; it only exposes deep-copied snapshots
 * to persistence/debug UI while keeping the graph itself authoritative.
 */
final class CognitiveGraphLiveBus {
    private static final ThreadLocal<JSONObject> THREAD_SNAPSHOT = new ThreadLocal<>();
    private static volatile String latestJson = "{}";

    private CognitiveGraphLiveBus() {
    }

    static void publish(JSONObject snapshot) {
        JSONObject safe = validCopy(snapshot);
        if (safe.length() == 0) return;
        THREAD_SNAPSHOT.set(safe);
        latestJson = safe.toString();
    }

    static JSONObject currentThreadSnapshot() {
        return copy(THREAD_SNAPSHOT.get());
    }

    static JSONObject latestSnapshot() {
        try {
            return new JSONObject(latestJson);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    static boolean isValid(JSONObject snapshot) {
        if (snapshot == null) return false;
        JSONArray nodes = snapshot.optJSONArray("nodes");
        JSONArray edges = snapshot.optJSONArray("edges");
        return nodes != null && nodes.length() > 0 && edges != null;
    }

    private static JSONObject validCopy(JSONObject snapshot) {
        JSONObject copy = copy(snapshot);
        return isValid(copy) ? copy : new JSONObject();
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
