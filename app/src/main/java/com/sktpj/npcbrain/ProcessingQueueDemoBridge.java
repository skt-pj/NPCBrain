package com.sktpj.npcbrain;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Observes DemoActivity's existing execution state without owning its scheduling.
 * This keeps the queue diagnostic-only and preserves the existing single-exclusion policy.
 */
final class ProcessingQueueDemoBridge {
    private static final long REFRESH_MS = 120L;
    private static final WeakHashMap<DemoActivityV032, State> STATES = new WeakHashMap<>();

    private ProcessingQueueDemoBridge() {}

    static synchronized void install(DemoActivityV032 activity) {
        if (activity == null || activity.isFinishing() || STATES.containsKey(activity)) return;
        State state = new State();
        STATES.put(activity, state);
        Handler handler = new Handler(Looper.getMainLooper());
        WeakReference<DemoActivityV032> reference = new WeakReference<>(activity);
        Runnable observer = new Runnable() {
            @Override
            public void run() {
                DemoActivityV032 target = reference.get();
                if (target == null || target.isFinishing()) return;
                observe(target, state);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(observer);
    }

    private static void observe(DemoActivityV032 activity, State state) {
        boolean processing = booleanField(activity, "processing");
        String roomId = stringField(activity, "processingRoomId");

        if (processing && !state.wasProcessing) {
            state.roomId = roomId;
            state.type = roomId.isEmpty() ? "spontaneous_cognition" : "conversation_reply";
            String npcId = roomId.isEmpty()
                    ? stringField(activity, "liveNpcId")
                    : npcForRoom(roomId);
            if ("conversation_reply".equals(state.type)) {
                state.entryId = ProcessingQueueRegistry.claimConversation(
                        roomId,
                        npcId,
                        System.currentTimeMillis());
            } else {
                state.entryId = ProcessingQueueRegistry.startRunning(
                        state.type,
                        npcId,
                        "foreground spontaneous",
                        System.currentTimeMillis());
            }
        } else if (!processing && state.wasProcessing && !state.entryId.isEmpty()) {
            if ("conversation_reply".equals(state.type)
                    && hasRetryForRoom(activity, state.roomId)) {
                ProcessingQueueRegistry.markFailed(
                        state.entryId,
                        System.currentTimeMillis(),
                        "返信処理に失敗。再試行可能");
            } else {
                ProcessingQueueRegistry.markCompleted(
                        state.entryId,
                        System.currentTimeMillis(),
                        state.roomId.isEmpty() ? state.type : "room=" + state.roomId);
            }
            state.clearActive();
        }

        state.wasProcessing = processing;
    }

    private static boolean hasRetryForRoom(DemoActivityV032 activity, String roomId) {
        Object message = rawField(activity, "retryUserMessage");
        if (!(message instanceof JSONObject)) return false;
        String retryRoom = stringField(activity, "retryRoomId");
        return retryRoom.equals(roomId == null ? "" : roomId);
    }

    private static String npcForRoom(String roomId) {
        String room = roomId == null ? "" : roomId.trim();
        return room.startsWith("direct_") ? room.substring("direct_".length()) : "";
    }

    private static boolean booleanField(DemoActivityV032 activity, String name) {
        Object value = rawField(activity, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static String stringField(DemoActivityV032 activity, String name) {
        Object value = rawField(activity, name);
        return value == null ? "" : value.toString().trim();
    }

    private static Object rawField(DemoActivityV032 activity, String name) {
        try {
            Field field = DemoActivityV032.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class State {
        boolean wasProcessing;
        String entryId = "";
        String type = "";
        String roomId = "";

        void clearActive() {
            entryId = "";
            type = "";
            roomId = "";
        }
    }
}
