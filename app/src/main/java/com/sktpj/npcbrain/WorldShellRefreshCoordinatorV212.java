package com.sktpj.npcbrain;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Keeps the one-world chrome live without rebuilding the visible UI every polling interval.
 * Re-rendering happens only when the content observed by the current tab actually changes.
 */
final class WorldShellRefreshCoordinatorV212 {
    private static final long REFRESH_MS = 750L;
    private static final WeakHashMap<WorldShellActivityV212, Session> SESSIONS = new WeakHashMap<>();

    private WorldShellRefreshCoordinatorV212() {}

    static synchronized void onResumed(WorldShellActivityV212 activity) {
        if (activity == null || activity.isFinishing()) return;
        removeLegacyRefresh(activity);
        Session session = SESSIONS.get(activity);
        if (session == null) {
            session = new Session(activity);
            SESSIONS.put(activity, session);
        }
        session.resume();
    }

    static synchronized void onPaused(WorldShellActivityV212 activity) {
        Session session = SESSIONS.get(activity);
        if (session != null) session.pause();
    }

    static synchronized void onDestroyed(WorldShellActivityV212 activity) {
        Session session = SESSIONS.remove(activity);
        if (session != null) session.pause();
    }

    private static void removeLegacyRefresh(WorldShellActivityV212 activity) {
        try {
            Field handlerField = activity.getClass().getDeclaredField("handler");
            handlerField.setAccessible(true);
            Object handlerObject = handlerField.get(activity);
            Field taskField = activity.getClass().getDeclaredField("refreshTask");
            taskField.setAccessible(true);
            Object taskObject = taskField.get(activity);
            if (handlerObject instanceof Handler && taskObject instanceof Runnable) {
                ((Handler) handlerObject).removeCallbacks((Runnable) taskObject);
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Session {
        final WorldShellActivityV212 activity;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable task = new Runnable() {
            @Override public void run() {
                if (!running || activity.isFinishing() || activity.isDestroyed()) return;
                tick();
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        boolean running;
        String contentSignature = "";

        Session(WorldShellActivityV212 activity) {
            this.activity = activity;
        }

        void resume() {
            running = true;
            contentSignature = "";
            handler.removeCallbacks(task);
            handler.post(task);
        }

        void pause() {
            running = false;
            handler.removeCallbacks(task);
        }

        void tick() {
            invoke(activity, "refreshWorldChrome", new Class<?>[]{boolean.class}, false);
            String next = signature(activity);
            if (next.equals(contentSignature)) return;
            boolean first = contentSignature.isEmpty();
            contentSignature = next;
            if (!first) invoke(activity, "renderCurrentTab", new Class<?>[0]);
        }
    }

    private static String signature(WorldShellActivityV212 activity) {
        Object tab = fieldValue(activity, "currentTab");
        String tabName = tab == null ? "" : tab.toString();
        WorldQueryServiceV210 query = NPCBrainApplication.worldQuery();
        if ("CONVERSATION".equals(tabName)) {
            ConversationStore conversations = new ConversationStore(activity);
            String room = stringField(activity, "currentRoomId");
            if (!room.isEmpty()) return tabName + ":room:" + room + ":" + conversations.messageCount(room);
            return tabName + ":list:" + conversationFingerprint(activity, conversations);
        }
        if ("STATUS".equals(tabName) || "DUNGEON".equals(tabName) || "CODEX".equals(tabName)) {
            long revision = query == null ? 0L : query.revision();
            String focus = stringField(activity, "focusedNpcId");
            return tabName + ":" + focus + ":" + revision;
        }
        // Settings is user-driven. World revision is already shown in the common chrome and must not
        // rebuild editable settings while the user is interacting with them.
        return tabName;
    }

    private static String conversationFingerprint(
            WorldShellActivityV212 activity,
            ConversationStore conversations
    ) {
        StringBuilder value = new StringBuilder();
        List<String> ids = new NpcRegistryStore(activity).activeNpcIds();
        for (String id : ids) {
            String room = "direct_" + id;
            value.append(room).append('=').append(conversations.messageCount(room)).append(';');
        }
        if (ids.size() >= 2) {
            value.append(DemoRuntimeV032.ROOM_GROUP).append('=')
                    .append(conversations.messageCount(DemoRuntimeV032.ROOM_GROUP)).append(';');
        }
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String peer = NpcPeerRoomPolicy.roomId(ids.get(i), ids.get(j));
                if (!peer.isEmpty()) {
                    value.append(peer).append('=').append(conversations.messageCount(peer)).append(';');
                }
            }
        }
        return value.toString();
    }

    private static Object fieldValue(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringField(Object target, String name) {
        Object value = fieldValue(target, name);
        return value == null ? "" : value.toString().trim();
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception ignored) {
        }
    }
}
