package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WorldCommitResultV210 {
    final boolean committed;
    final boolean duplicate;
    final long revision;
    final long worldTimeMs;
    final List<WorldEventV210> events;
    final JSONObject result;

    WorldCommitResultV210(
            boolean committed,
            boolean duplicate,
            long revision,
            long worldTimeMs,
            List<WorldEventV210> events,
            JSONObject result
    ) {
        this.committed = committed;
        this.duplicate = duplicate;
        this.revision = Math.max(0L, revision);
        this.worldTimeMs = Math.max(0L, worldTimeMs);
        this.events = events == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(events));
        this.result = copy(result);
    }

    JSONObject toJson() {
        JSONObject json = copy(result);
        JSONArray eventIds = new JSONArray();
        for (WorldEventV210 event : events) eventIds.put(event.eventId);
        try {
            json.put("committed", committed);
            json.put("duplicate", duplicate);
            json.put("revision", revision);
            json.put("world_time_ms", worldTimeMs);
            json.put("event_ids", eventIds);
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
}
