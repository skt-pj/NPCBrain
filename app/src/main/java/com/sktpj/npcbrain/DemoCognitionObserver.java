package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;

final class DemoCognitionObserver {
    static final class Snapshot {
        final JSONArray stages;
        final boolean live;
        final long timeMs;

        Snapshot(JSONArray stages, boolean live, long timeMs) {
            this.stages = copy(stages);
            this.live = live;
            this.timeMs = timeMs;
        }
    }

    private static final String[] ROOMS = {
            DemoRuntimeV032.ROOM_NPC1,
            DemoRuntimeV032.ROOM_NPC2,
            DemoRuntimeV032.ROOM_GROUP
    };

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
            if (!(stages instanceof JSONArray)) return null;
            return new Snapshot((JSONArray) stages, true, System.currentTimeMillis());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Snapshot storedSnapshot(Context context, String npcId) {
        ConversationStore store = new ConversationStore(context.getApplicationContext());
        JSONArray best = new JSONArray();
        long bestTime = 0L;
        for (String roomId : ROOMS) {
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
                }
            }
        }
        return new Snapshot(best, false, bestTime);
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
}
