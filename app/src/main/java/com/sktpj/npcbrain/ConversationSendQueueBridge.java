package com.sktpj.npcbrain;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

final class ConversationSendQueueBridge {
    private static final long REFRESH_MS = 180L;
    private static final WeakHashMap<DemoActivityV032, BridgeState> STATES = new WeakHashMap<>();

    private ConversationSendQueueBridge() {
    }

    static synchronized void install(DemoActivityV032 activity) {
        if (activity == null || activity.isFinishing()) return;
        if (STATES.containsKey(activity)) return;
        BridgeState state = new BridgeState();
        STATES.put(activity, state);
        Handler handler = new Handler(Looper.getMainLooper());
        WeakReference<DemoActivityV032> reference = new WeakReference<>(activity);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                DemoActivityV032 target = reference.get();
                if (target == null || target.isFinishing()) return;
                View content = target.findViewById(android.R.id.content);
                if (content == null || content.getWindowToken() == null) {
                    handler.postDelayed(this, REFRESH_MS);
                    return;
                }
                refresh(target, state);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(task);
    }

    private static void refresh(DemoActivityV032 activity, BridgeState state) {
        boolean processing = booleanField(activity, "processing");
        String processingRoomId = stringFieldNullable(activity, "processingRoomId");
        String currentRoomId = stringFieldNullable(activity, "currentRoomId");

        if (!processing && state.queuedUserMessage != null) {
            startQueued(activity, state);
            processing = booleanField(activity, "processing");
            processingRoomId = stringFieldNullable(activity, "processingRoomId");
        }

        Button send = objectField(activity, "sendButton", Button.class);
        if (send != null) {
            boolean hasQueued = state.queuedUserMessage != null;
            send.setEnabled(currentRoomId != null
                    && ConversationUiPolicy.canSubmitMessage(processing, processingRoomId, hasQueued));
            if (send != state.boundSendButton) {
                state.boundSendButton = send;
                send.setOnClickListener(v -> submit(activity, state));
            }
        }

        if (state.queuedUserMessage != null && equals(state.queuedRoomId, currentRoomId)) {
            TextView typing = objectField(activity, "typingStatus", TextView.class);
            if (typing != null) {
                typing.setVisibility(View.VISIBLE);
                typing.setClickable(false);
                typing.setText("送信待ち · バックグラウンド処理後に返信処理を開始します");
            }
        } else {
            invokeNoArgs(activity, "updateTypingStatus");
            TextView typing = objectField(activity, "typingStatus", TextView.class);
            if (typing != null) typing.setClickable(true);
        }
    }

    private static void submit(DemoActivityV032 activity, BridgeState state) {
        String currentRoomId = stringFieldNullable(activity, "currentRoomId");
        if (currentRoomId == null) return;
        boolean processing = booleanField(activity, "processing");
        String processingRoomId = stringFieldNullable(activity, "processingRoomId");
        if (!ConversationUiPolicy.canSubmitMessage(
                processing,
                processingRoomId,
                state.queuedUserMessage != null)) return;

        if (!processing) {
            invokeNoArgs(activity, "sendCurrentMessage");
            return;
        }

        EditText input = objectField(activity, "messageInput", EditText.class);
        if (input == null) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;

        SecureApiKeyStore apiKeyStore = objectField(activity, "apiKeyStore", SecureApiKeyStore.class);
        String apiKey;
        try {
            apiKey = apiKeyStore == null ? "" : apiKeyStore.load().trim();
        } catch (Exception error) {
            invokeError(activity, "APIキーを読み出せません: " + safeMessage(error), false);
            return;
        }
        if (apiKey.isEmpty()) {
            invokeNoArgs(activity, "showMissingApiKeyDialog");
            return;
        }

        invokeNoArgs(activity, "hideKeyboard");
        ConversationStore conversations = objectField(activity, "conversationStore", ConversationStore.class);
        if (conversations == null) conversations = new ConversationStore(activity);
        JSONObject userMessage = conversations.appendUserMessage(
                currentRoomId,
                text,
                System.currentTimeMillis());
        input.setText("");
        state.queuedRoomId = currentRoomId;
        state.queuedUserMessage = userMessage;
        invokeNoArgs(activity, "refreshMessages");
        refresh(activity, state);
    }

    private static void startQueued(DemoActivityV032 activity, BridgeState state) {
        JSONObject queuedMessage = state.queuedUserMessage;
        String queuedRoom = state.queuedRoomId;
        if (queuedMessage == null || queuedRoom == null) {
            state.clearQueue();
            return;
        }

        state.clearQueue();
        SecureApiKeyStore apiKeyStore = objectField(activity, "apiKeyStore", SecureApiKeyStore.class);
        String apiKey;
        try {
            apiKey = apiKeyStore == null ? "" : apiKeyStore.load().trim();
        } catch (Exception error) {
            setRetry(activity, queuedRoom, queuedMessage);
            invokeError(activity, "送信待ちメッセージの処理を開始できません: " + safeMessage(error), true);
            return;
        }
        if (apiKey.isEmpty()) {
            setRetry(activity, queuedRoom, queuedMessage);
            invokeError(activity, "APIキーが未設定のため、送信待ちメッセージの返信処理を開始できません。", true);
            return;
        }

        try {
            Method method = DemoActivityV032.class.getDeclaredMethod(
                    "startProcessingEvent",
                    String.class,
                    JSONObject.class,
                    String.class);
            method.setAccessible(true);
            method.invoke(activity, queuedRoom, queuedMessage, apiKey);
        } catch (Exception error) {
            setRetry(activity, queuedRoom, queuedMessage);
            invokeError(activity, "送信待ちメッセージの返信処理を開始できません: " + safeMessage(error), true);
        }
    }

    private static void setRetry(DemoActivityV032 activity, String roomId, JSONObject message) {
        setField(activity, "retryRoomId", roomId);
        setField(activity, "retryUserMessage", message);
    }

    private static void invokeError(DemoActivityV032 activity, String message, boolean retry) {
        try {
            Method method = DemoActivityV032.class.getDeclaredMethod(
                    "showErrorDialog",
                    String.class,
                    boolean.class);
            method.setAccessible(true);
            method.invoke(activity, message, retry);
        } catch (Exception ignored) {
        }
    }

    private static void invokeNoArgs(DemoActivityV032 activity, String methodName) {
        try {
            Method method = DemoActivityV032.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception ignored) {
        }
    }

    private static boolean booleanField(DemoActivityV032 activity, String name) {
        try {
            Field field = DemoActivityV032.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String stringFieldNullable(DemoActivityV032 activity, String name) {
        Object value = rawField(activity, name);
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static <T> T objectField(DemoActivityV032 activity, String name, Class<T> type) {
        Object value = rawField(activity, name);
        return type.isInstance(value) ? type.cast(value) : null;
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

    private static void setField(DemoActivityV032 activity, String name, Object value) {
        try {
            Field field = DemoActivityV032.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(activity, value);
        } catch (Exception ignored) {
        }
    }

    private static boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String safeMessage(Exception error) {
        if (error == null) return "不明なエラー";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.toString() : message;
    }

    private static final class BridgeState {
        Button boundSendButton;
        JSONObject queuedUserMessage;
        String queuedRoomId;

        void clearQueue() {
            queuedUserMessage = null;
            queuedRoomId = null;
        }
    }
}
