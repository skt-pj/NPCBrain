package com.sktpj.npcbrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local diagnostic state for logical work and LLM execution. */
final class ProcessingQueueRegistry {
    static final int MAX_TERMINAL_HISTORY = 100;

    enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    static final class Entry {
        final String id;
        final String type;
        final String npcId;
        final String detail;
        final Status status;
        final long queuedAtMs;
        final long startedAtMs;
        final long finishedAtMs;
        final String error;

        Entry(
                String id,
                String type,
                String npcId,
                String detail,
                Status status,
                long queuedAtMs,
                long startedAtMs,
                long finishedAtMs,
                String error
        ) {
            this.id = safe(id);
            this.type = safe(type);
            this.npcId = safe(npcId);
            this.detail = safe(detail);
            this.status = status == null ? Status.QUEUED : status;
            this.queuedAtMs = queuedAtMs;
            this.startedAtMs = startedAtMs;
            this.finishedAtMs = finishedAtMs;
            this.error = safe(error);
        }

        boolean isActive() {
            return status == Status.QUEUED || status == Status.RUNNING;
        }
    }

    static final class Snapshot {
        final List<Entry> active;
        final List<Entry> recent;

        Snapshot(List<Entry> active, List<Entry> recent) {
            this.active = Collections.unmodifiableList(active);
            this.recent = Collections.unmodifiableList(recent);
        }
    }

    private static final Object LOCK = new Object();
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final Map<String, Entry> ACTIVE = new LinkedHashMap<>();
    private static final ArrayDeque<Entry> TERMINAL = new ArrayDeque<>();
    private static final ThreadLocal<String> CURRENT_LOCAL_LLM_ENTRY = new ThreadLocal<>();

    private ProcessingQueueRegistry() {}

    static String enqueue(String type, String npcId, String detail) {
        return enqueue(type, npcId, detail, System.currentTimeMillis());
    }

    static String enqueue(String type, String npcId, String detail, long nowMs) {
        long now = Math.max(0L, nowMs);
        String id = "q" + NEXT_ID.getAndIncrement();
        Entry entry = new Entry(
                id,
                safe(type),
                safe(npcId),
                safe(detail),
                Status.QUEUED,
                now,
                0L,
                0L,
                "");
        synchronized (LOCK) {
            ACTIVE.put(id, entry);
        }
        return id;
    }

    static String startRunning(String type, String npcId, String detail) {
        return startRunning(type, npcId, detail, System.currentTimeMillis());
    }

    static String startRunning(String type, String npcId, String detail, long nowMs) {
        String id = enqueue(type, npcId, detail, nowMs);
        if (isLocalLlmRequest(type, detail)) {
            // The entry is genuinely waiting until LocalLlmExecutionQueue grants the single slot.
            CURRENT_LOCAL_LLM_ENTRY.set(id);
            return id;
        }
        markRunning(id, nowMs);
        return id;
    }

    static String currentLocalLlmEntryId() {
        return safe(CURRENT_LOCAL_LLM_ENTRY.get());
    }

    private static boolean isLocalLlmRequest(String type, String detail) {
        return "llm_request".equals(safe(type)) && safe(detail).startsWith("local_");
    }

    /** One pending direct-room reply is already enforced by ConversationSendQueueBridge. */
    static String enqueueConversationWait(String roomId, String npcId, long nowMs) {
        String room = safe(roomId);
        synchronized (LOCK) {
            for (Entry entry : ACTIVE.values()) {
                if (entry.status == Status.QUEUED
                        && "conversation_reply".equals(entry.type)
                        && roomTag(room).equals(entry.detail)) {
                    return entry.id;
                }
            }
        }
        return enqueue("conversation_reply", npcId, roomTag(room), nowMs);
    }

    static String claimConversation(String roomId, String npcId, long nowMs) {
        String room = safe(roomId);
        String id = "";
        synchronized (LOCK) {
            for (Entry entry : ACTIVE.values()) {
                if (entry.status == Status.QUEUED
                        && "conversation_reply".equals(entry.type)
                        && roomTag(room).equals(entry.detail)) {
                    id = entry.id;
                    break;
                }
            }
        }
        if (id.isEmpty()) {
            id = enqueue("conversation_reply", npcId, roomTag(room), nowMs);
        }
        markRunning(id, nowMs);
        return id;
    }

    static void markRunning(String id) {
        markRunning(id, System.currentTimeMillis());
    }

    static void markRunning(String id, long nowMs) {
        synchronized (LOCK) {
            Entry old = ACTIVE.get(safe(id));
            if (old == null || !old.isActive()) return;
            long started = old.startedAtMs > 0L ? old.startedAtMs : Math.max(old.queuedAtMs, nowMs);
            ACTIVE.put(old.id, new Entry(
                    old.id,
                    old.type,
                    old.npcId,
                    old.detail,
                    Status.RUNNING,
                    old.queuedAtMs,
                    started,
                    0L,
                    ""));
        }
    }

    static void markCompleted(String id) {
        markCompleted(id, System.currentTimeMillis(), "");
    }

    static void markCompleted(String id, long nowMs, String detail) {
        finish(id, Status.COMPLETED, nowMs, detail, "");
    }

    static void markFailed(String id, Throwable error) {
        markFailed(id, System.currentTimeMillis(), error == null ? "" : rootMessage(error));
    }

    static void markFailed(String id, long nowMs, String error) {
        finish(id, Status.FAILED, nowMs, "", error);
    }

    private static void finish(
            String id,
            Status status,
            long nowMs,
            String replacementDetail,
            String error
    ) {
        String key = safe(id);
        if (key.equals(safe(CURRENT_LOCAL_LLM_ENTRY.get()))) {
            CURRENT_LOCAL_LLM_ENTRY.remove();
        }
        synchronized (LOCK) {
            Entry old = ACTIVE.remove(key);
            if (old == null || !old.isActive()) return;
            long finished = Math.max(
                    old.startedAtMs > 0L ? old.startedAtMs : old.queuedAtMs,
                    Math.max(0L, nowMs));
            Entry terminal = new Entry(
                    old.id,
                    old.type,
                    old.npcId,
                    safe(replacementDetail).isEmpty() ? old.detail : safe(replacementDetail),
                    status,
                    old.queuedAtMs,
                    old.startedAtMs,
                    finished,
                    safe(error));
            TERMINAL.addFirst(terminal);
            while (TERMINAL.size() > MAX_TERMINAL_HISTORY) {
                TERMINAL.removeLast();
            }
        }
    }

    static Snapshot snapshot() {
        synchronized (LOCK) {
            List<Entry> active = new ArrayList<>(ACTIVE.values());
            active.sort(Comparator
                    .comparingInt((Entry entry) -> entry.status == Status.RUNNING ? 0 : 1)
                    .thenComparingLong(entry -> entry.queuedAtMs));
            return new Snapshot(active, new ArrayList<>(TERMINAL));
        }
    }

    static int activeCount() {
        synchronized (LOCK) {
            return ACTIVE.size();
        }
    }

    static String displayType(String type) {
        String value = safe(type);
        if ("conversation_reply".equals(value)) return "会話返信";
        if ("spontaneous_cognition".equals(value)) return "自発送信判断";
        if ("llm_request".equals(value)) return "LLM推論";
        if ("memory_maintenance".equals(value)) return "記憶整理";
        if ("periodic_social".equals(value)) return "NPC間会話判断";
        if ("reply_timer".equals(value)) return "返信タイマー再判断";
        if ("local_model_download".equals(value)) return "モデルダウンロード";
        if ("dungeon_brain".equals(value)) return "ダンジョン思考";
        if ("ambient_thought".equals(value)) return "自律思考";
        return value.isEmpty() ? "処理" : value;
    }

    static String rootMessage(Throwable error) {
        if (error == null) return "";
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = current.getClass().getSimpleName();
        }
        String text = message.trim();
        return text.length() <= 240 ? text : text.substring(0, 240) + "…";
    }

    private static String roomTag(String roomId) {
        return "room=" + safe(roomId);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static void clearForTests() {
        synchronized (LOCK) {
            ACTIVE.clear();
            TERMINAL.clear();
            NEXT_ID.set(1L);
        }
        CURRENT_LOCAL_LLM_ENTRY.remove();
    }
}
