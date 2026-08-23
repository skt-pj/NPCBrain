package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

final class DemoCognitionObserver {
    static final class Snapshot {
        final JSONArray stages;
        final JSONObject cognitiveGraph;
        final boolean live;
        final long timeMs;

        Snapshot(JSONArray stages, JSONObject cognitiveGraph, boolean live, long timeMs) {
            this.stages = copy(stages);
            this.cognitiveGraph = copy(cognitiveGraph);
            this.live = live;
            this.timeMs = timeMs;
        }
    }

    private DemoCognitionObserver() {
    }

    static Snapshot snapshot(Context context, String npcId) {
        Snapshot live = liveSnapshot(npcId);
        if (live != null) return live;
        return storedSnapshot(context, npcId);
    }

    private static Snapshot liveSnapshot(String npcId) {
        DemoActivityV032 activity = NPCBrainApplication.currentDemoActivity();
        if (activity == null) return null;
        try {
            Field processingField = DemoActivityV032.class.getDeclaredField("processing");
            Field npcField = DemoActivityV032.class.getDeclaredField("liveNpcId");
            Field stagesField = DemoActivityV032.class.getDeclaredField("liveStages");
            processingField.setAccessible(true);
            npcField.setAccessible(true);
            stagesField.setAccessible(true);
            boolean processing = processingField.getBoolean(activity);
            String liveNpcId = String.valueOf(npcField.get(activity));
            if (!processing || !npcId.equals(liveNpcId)) return null;
            Object stages = stagesField.get(activity);
            JSONArray copiedStages = stages instanceof JSONArray
                    ? copy((JSONArray) stages)
                    : new JSONArray();
            JSONObject actualGraph = CognitiveGraphLiveBus.latestSnapshot();
            if (!CognitiveGraphLiveBus.isValid(actualGraph)) actualGraph = new JSONObject();
            return new Snapshot(copiedStages, actualGraph, true, System.currentTimeMillis());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Snapshot storedSnapshot(Context context, String npcId) {
        ConversationStore store = new ConversationStore(context.getApplicationContext());
        JSONArray best = new JSONArray();
        JSONObject bestGraph = new JSONObject();
        long bestTime = 0L;
        for (String roomId : roomsFor(npcId)) {
            JSONArray messages = store.messages(roomId);
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message == null || !belongsToNpc(message.optString("sender_id", ""), npcId)) continue;
                JSONArray trace = message.optJSONArray("brain_trace");
                if (trace == null || trace.length() == 0) continue;
                long time = message.optLong("time_ms", 0L);
                if (time >= bestTime) {
                    bestTime = time;
                    best = copy(trace);
                    bestGraph = graphFromTrace(trace);
                }
            }
        }
        return new Snapshot(best, bestGraph, false, bestTime);
    }

    private static List<String> roomsFor(String npcId) {
        List<String> rooms = new ArrayList<>();
        String id;
        try {
            id = NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return rooms;
        }
        rooms.add("direct_" + id);
        if ("npc1".equals(id) || "npc2".equals(id)) rooms.add(DemoRuntimeV032.ROOM_GROUP);
        return rooms;
    }

    static JSONObject graphFromTrace(JSONArray trace) {
        if (trace == null) return new JSONObject();
        for (int i = trace.length() - 1; i >= 0; i--) {
            JSONObject stage = trace.optJSONObject(i);
            if (stage == null || !"global_workspace".equals(stage.optString("stage_id", ""))) continue;
            JSONObject graph = stage.optJSONObject("cognitive_graph");
            if (CognitiveGraphLiveBus.isValid(graph)) return copy(graph);
            return new JSONObject();
        }
        return new JSONObject();
    }

    private static boolean belongsToNpc(String senderId, String npcId) {
        if (npcId == null || npcId.trim().isEmpty()) return false;
        String sender = senderId == null ? "" : senderId.trim();
        return npcId.equals(sender)
                || ("decision_" + npcId).equals(sender)
                || ("runtime_decision_" + npcId).equals(sender);
    }

    private static JSONArray copy(JSONArray source) {
        try {
            return source == null ? new JSONArray() : new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
