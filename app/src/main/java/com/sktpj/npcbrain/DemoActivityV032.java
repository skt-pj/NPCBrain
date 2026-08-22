package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DemoActivityV032 extends Activity {
    private static final class TraitControl {
        final LinearLayout root;
        final SeekBar seek;
        TraitControl(LinearLayout root, SeekBar seek) {
            this.root = root;
            this.seek = seek;
        }
    }

    private SecureApiKeyStore apiKeyStore;
    private ModelSettingsStore modelSettingsStore;
    private ConversationStore conversationStore;
    private DemoRuntimeV032 demoRuntime;

    private LinearLayout screenContainer;
    private TextView titleView;
    private TextView subtitleView;
    private Button backButton;
    private Button menuButton;

    private String currentRoomId;
    private ScrollView chatScroll;
    private LinearLayout messageContainer;
    private EditText messageInput;
    private Button sendButton;
    private TextView typingStatus;
    private volatile boolean processing;
    private volatile String processingRoomId;

    private String liveNpcName = "NPC";
    private String liveNpcId = "";
    private JSONArray liveStages = new JSONArray();
    private boolean liveDone;
    private AlertDialog liveBrainDialog;
    private LinearLayout liveBrainContent;

    private JSONObject retryUserMessage;
    private String retryRoomId;

    private OnBackInvokedCallback chatBackCallback;
    private boolean chatBackCallbackRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        conversationStore = new ConversationStore(this);
        demoRuntime = new DemoRuntimeV032(this, conversationStore);
        if (Build.VERSION.SDK_INT >= 33) {
            chatBackCallback = this::showRoomList;
        }
        setContentView(buildShell());
        showRoomList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeStartSpontaneousProcessing();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && chatBackCallbackRegistered && chatBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(chatBackCallback);
            chatBackCallbackRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (ConversationUiPolicy.consumesSystemBack(currentRoomId)) {
            showRoomList();
            return;
        }
        super.onBackPressed();
    }

    private void updateSystemBackCallback() {
        if (Build.VERSION.SDK_INT < 33 || chatBackCallback == null) return;
        boolean shouldRegister = ConversationUiPolicy.consumesSystemBack(currentRoomId);
        if (shouldRegister == chatBackCallbackRegistered) return;
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (shouldRegister) {
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    chatBackCallback);
        } else {
            dispatcher.unregisterOnBackInvokedCallback(chatBackCallback);
        }
        chatBackCallbackRegistered = shouldRegister;
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 249, 251));
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(60));

        backButton = new Button(this);
        backButton.setText("‹");
        backButton.setTextSize(30);
        backButton.setMinWidth(0);
        backButton.setMinimumWidth(0);
        backButton.setPadding(0, 0, 0, 0);
        backButton.setContentDescription("トーク一覧に戻る");
        backButton.setOnClickListener(v -> showRoomList());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(4);
        header.addView(titleBox, titleParams);

        titleView = new TextView(this);
        titleView.setTextColor(Color.rgb(24, 24, 28));
        titleView.setTextSize(21);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(titleView);

        subtitleView = new TextView(this);
        subtitleView.setTextColor(Color.rgb(102, 102, 112));
        subtitleView.setTextSize(11);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(subtitleView);

        menuButton = new Button(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(24);
        menuButton.setMinWidth(0);
        menuButton.setMinimumWidth(0);
        menuButton.setPadding(0, 0, 0, 0);
        menuButton.setContentDescription("ホーム設定");
        menuButton.setOnClickListener(v -> showHomeMenu(menuButton));
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        screenContainer = new LinearLayout(this);
        screenContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(screenContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void showRoomList() {
        currentRoomId = null;
        updateSystemBackCallback();
        hideKeyboard();
        backButton.setVisibility(View.INVISIBLE);
        menuButton.setVisibility(View.VISIBLE);
        titleView.setText("NPCBrain Demo");
        subtitleView.setText("会話デモ / 脳内トレース付き");
        screenContainer.removeAllViews();

        TextView model = new TextView(this);
        model.setText(currentModelSummary());
        model.setTextColor(Color.rgb(52, 67, 101));
        model.setTextSize(12);
        model.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(4);
        mp.bottomMargin = dp(12);
        screenContainer.addView(model, mp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rooms = new LinearLayout(this);
        rooms.setOrientation(LinearLayout.VERTICAL);
        rooms.setPadding(0, 0, 0, dp(24));
        for (String roomId : demoRuntime.roomIds()) rooms.addView(createRoomRow(roomId));
        scroll.addView(rooms);
        screenContainer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View createRoomRow(String roomId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setMinimumHeight(dp(76));
        row.setBackground(cardBackground(Color.WHITE, Color.rgb(225, 227, 232), 14));
        row.setOnClickListener(v -> openRoom(roomId));
        row.setClickable(true);

        TextView title = new TextView(this);
        title.setText(demoRuntime.roomTitle(roomId));
        title.setTextColor(Color.rgb(32, 32, 38));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title);

        JSONObject last = conversationStore.lastMessage(roomId);
        TextView preview = new TextView(this);
        if (last == null) {
            preview.setText("まだ会話はありません");
        } else if (ConversationStore.isDebugDecisionSender(last.optString("sender_id", ""))) {
            preview.setText(last.optString("sender_name", "NPC（返信なし）"));
        } else {
            preview.setText(last.optString("sender_name", "") + ": " + last.optString("text", ""));
        }
        preview.setTextColor(Color.rgb(102, 102, 112));
        preview.setTextSize(13);
        preview.setMaxLines(1);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(5);
        row.addView(preview, pp);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);
        return row;
    }

    private void openRoom(String roomId) {
        currentRoomId = roomId;
        updateSystemBackCallback();
        backButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.GONE);
        titleView.setText(demoRuntime.roomTitle(roomId));
        subtitleView.setText(demoRuntime.roomSubtitle(roomId));
        renderChatScreen();
    }

    private void renderChatScreen() {
        if (currentRoomId == null) return;
        screenContainer.removeAllViews();

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        messageContainer = new LinearLayout(this);
        messageContainer.setOrientation(LinearLayout.VERTICAL);
        messageContainer.setPadding(0, dp(8), 0, dp(12));
        chatScroll.addView(messageContainer);
        screenContainer.addView(chatScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        typingStatus = new TextView(this);
        typingStatus.setText("");
        typingStatus.setTextColor(Color.rgb(72, 82, 112));
        typingStatus.setTextSize(12);
        typingStatus.setTypeface(Typeface.DEFAULT_BOLD);
        typingStatus.setGravity(Gravity.CENTER_VERTICAL);
        typingStatus.setPadding(dp(10), dp(8), dp(10), dp(8));
        typingStatus.setMinHeight(dp(40));
        typingStatus.setVisibility(isProcessingCurrentRoom() ? View.VISIBLE : View.GONE);
        typingStatus.setBackground(cardBackground(
                Color.rgb(240, 243, 250), Color.rgb(215, 220, 232), 12));
        typingStatus.setClickable(true);
        typingStatus.setContentDescription("入力中のNPCの脳内を見る");
        typingStatus.setOnClickListener(v -> showLiveBrainDialog());
        screenContainer.addView(typingStatus);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(0, dp(4), 0, dp(4));

        messageInput = new EditText(this);
        messageInput.setHint("メッセージ");
        messageInput.setTextSize(16);
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(messageInput, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new Button(this);
        sendButton.setText("送信");
        sendButton.setTextSize(13);
        sendButton.setTypeface(Typeface.DEFAULT_BOLD);
        sendButton.setEnabled(!processing);
        sendButton.setOnClickListener(v -> sendCurrentMessage());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(72), dp(52));
        sp.leftMargin = dp(6);
        composer.addView(sendButton, sp);
        screenContainer.addView(composer);

        refreshMessages();
        updateTypingStatus();
    }

    private void refreshMessages() {
        if (currentRoomId == null || messageContainer == null) return;
        messageContainer.removeAllViews();
        JSONArray messages = conversationStore.messages(currentRoomId);
        if (messages.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("まだメッセージはありません");
            empty.setTextColor(Color.rgb(126, 126, 136));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(42), dp(20), dp(42));
            messageContainer.addView(empty);
        } else {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message != null) messageContainer.addView(createMessageView(message));
            }
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private View createMessageView(JSONObject message) {
        String senderId = message.optString("sender_id", "");
        boolean user = "user".equals(senderId);
        boolean silentDecision = ConversationStore.isDebugDecisionSender(senderId);
        JSONArray trace = message.optJSONArray("brain_trace");
        boolean hasTrace = trace != null && trace.length() > 0;

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(user ? Gravity.END : Gravity.START);
        wrapper.setPadding(0, dp(4), 0, dp(4));

        if (silentDecision) {
            TextView silent = new TextView(this);
            silent.setText(message.optString("sender_name", "NPC（返信なし）"));
            silent.setTextColor(Color.rgb(92, 92, 102));
            silent.setTextSize(12);
            silent.setPadding(dp(4), dp(5), dp(4), dp(5));
            silent.setOnClickListener(v -> showMessageDetails(message));
            silent.setClickable(true);
            silent.setContentDescription("NPC判断の脳内を見る");
            wrapper.addView(silent);
            return wrapper;
        }

        if (!user) {
            TextView sender = new TextView(this);
            sender.setText(message.optString("sender_name", "NPC"));
            sender.setTextColor(Color.rgb(92, 92, 102));
            sender.setTextSize(11);
            wrapper.addView(sender);
        }

        TextView bubble = new TextView(this);
        bubble.setText(message.optString("text", ""));
        bubble.setTextSize(16);
        bubble.setTextColor(Color.rgb(28, 28, 32));
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setMaxWidth(dp(310));
        bubble.setBackground(cardBackground(
                user ? Color.rgb(211, 241, 204) : Color.WHITE,
                user ? Color.rgb(181, 221, 172) : Color.rgb(224, 226, 231), 16));
        bubble.setOnClickListener(v -> showMessageDetails(message));
        bubble.setClickable(true);
        wrapper.addView(bubble);

        TextView meta = new TextView(this);
        String metaText = formatTime(message.optLong("time_ms", 0L));
        if (hasTrace) metaText += " · 脳内を見る";
        meta.setText(metaText);
        meta.setTextColor(Color.rgb(130, 130, 140));
        meta.setTextSize(10);
        wrapper.addView(meta);
        return wrapper;
    }

    private void sendCurrentMessage() {
        if (processing || currentRoomId == null || messageInput == null) return;
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            showErrorDialog("APIキーを読み出せません: " + error.getMessage(), false);
            return;
        }
        if (apiKey.isEmpty()) {
            showMissingApiKeyDialog();
            return;
        }

        hideKeyboard();
        JSONObject userMessage = conversationStore.appendUserMessage(
                currentRoomId, text, System.currentTimeMillis());
        messageInput.setText("");
        retryUserMessage = null;
        retryRoomId = null;
        refreshMessages();
        startProcessingEvent(currentRoomId, userMessage, apiKey);
    }

    private void startProcessingEvent(String roomId, JSONObject userMessage, String apiKey) {
        final String effort = modelSettingsStore.reasoningEffort();
        processing = true;
        processingRoomId = roomId;
        liveDone = false;
        initializeLiveStages();
        liveNpcId = firstNpcId(roomId);
        liveNpcName = demoRuntime.displayName(liveNpcId);
        if (sendButton != null) sendButton.setEnabled(false);
        updateTypingStatus();

        new Thread(() -> {
            try {
                demoRuntime.processUserMessage(
                        roomId,
                        userMessage,
                        apiKey,
                        effort,
                        new DemoRuntimeV032.Listener() {
                            @Override
                            public void onNpcStarted(String npcId, String displayName, int current, int total) {
                                runOnUiThread(() -> {
                                    liveNpcId = npcId;
                                    liveNpcName = displayName;
                                    liveDone = false;
                                    initializeLiveStages();
                                    updateTypingStatus();
                                    renderLiveBrainDialogIfOpen();
                                });
                            }

                            @Override
                            public void onStageStarted(
                                    String npcId,
                                    String displayName,
                                    String stageId,
                                    String stageLabel,
                                    int current,
                                    int total
                            ) {
                                runOnUiThread(() -> {
                                    markStageStarted(stageId, stageLabel, current, total);
                                    updateTypingStatus();
                                    renderLiveBrainDialogIfOpen();
                                });
                            }

                            @Override
                            public void onStageCompleted(
                                    String npcId,
                                    String displayName,
                                    String stageId,
                                    String stageLabel,
                                    int current,
                                    int total,
                                    String summary,
                                    double confidence,
                                    JSONArray salientFacts,
                                    String personalityEffect,
                                    String model,
                                    String reasoningEffort
                            ) {
                                runOnUiThread(() -> {
                                    markStageCompleted(
                                            stageId, stageLabel, current, total, summary,
                                            confidence, salientFacts, personalityEffect,
                                            model, reasoningEffort);
                                    renderLiveBrainDialogIfOpen();
                                });
                            }

                            @Override
                            public void onNpcFinished(String npcId, String displayName, boolean sentMessage) {
                                runOnUiThread(() -> {
                                    liveDone = true;
                                    if (roomId.equals(currentRoomId)) refreshMessages();
                                    renderLiveBrainDialogIfOpen();
                                });
                            }
                        }
                );
                runOnUiThread(() -> finishProcessing(roomId, null, null));
            } catch (Exception error) {
                String msg = error.getMessage() == null ? error.toString() : error.getMessage();
                runOnUiThread(() -> finishProcessing(roomId, msg, userMessage));
            }
        }, "npcbrain-v032-message").start();
    }

    private void maybeStartSpontaneousProcessing() {
        if (processing || currentRoomId != null || demoRuntime == null || apiKeyStore == null) return;
        final String apiKey;
        try {
            apiKey = apiKeyStore.load().trim();
        } catch (Exception ignored) {
            return;
        }
        if (apiKey.isEmpty() || !demoRuntime.hasDueSpontaneousEvents()) return;
        startSpontaneousProcessing(apiKey);
    }

    private void startSpontaneousProcessing(String apiKey) {
        final String effort = modelSettingsStore.reasoningEffort();
        processing = true;
        processingRoomId = null;
        liveDone = false;
        initializeLiveStages();
        liveNpcId = "npc1";
        liveNpcName = demoRuntime.displayName(liveNpcId);
        if (sendButton != null) sendButton.setEnabled(false);
        updateTypingStatus();

        new Thread(() -> {
            try {
                demoRuntime.processPendingSpontaneous(
                        apiKey,
                        effort,
                        new DemoRuntimeV032.Listener() {
                            @Override
                            public void onNpcStarted(String npcId, String displayName, int current, int total) {
                                runOnUiThread(() -> {
                                    liveNpcId = npcId;
                                    liveNpcName = displayName;
                                    liveDone = false;
                                    initializeLiveStages();
                                    renderLiveBrainDialogIfOpen();
                                });
                            }

                            @Override
                            public void onStageStarted(
                                    String npcId,
                                    String displayName,
                                    String stageId,
                                    String stageLabel,
                                    int current,
                                    int total
                            ) {
                                runOnUiThread(() -> {
                                    markStageStarted(stageId, stageLabel, current, total);
                                    renderLiveBrainDialogIfOpen();
                                });
                            }

                            @Override
                            public void onStageCompleted(
                                    String npcId,
                                    String displayName,
                                    String stageId,
                                    String stageLabel,
                                    int current,
                                    int total,
                                    String summary,
                                    double confidence,
                                    JSONArray salientFacts,
                                    String personalityEffect,
                                    String model,
                                    String reasoningEffort
                            ) {
                                runOnUiThread(() -> {
                                    markStageCompleted(
                                            stageId, stageLabel, current, total, summary,
                                            confidence, salientFacts, personalityEffect,
                                            model, reasoningEffort);
                                    renderLiveBrainDialogIfOpen();
                                });
                            }

                            @Override
                            public void onNpcFinished(String npcId, String displayName, boolean sentMessage) {
                                runOnUiThread(() -> {
                                    liveDone = true;
                                    renderLiveBrainDialogIfOpen();
                                });
                            }
                        }
                );
                runOnUiThread(() -> finishSpontaneousProcessing(null));
            } catch (Exception error) {
                String msg = error.getMessage() == null ? error.toString() : error.getMessage();
                runOnUiThread(() -> finishSpontaneousProcessing(msg));
            }
        }, "npcbrain-v043-spontaneous").start();
    }

    private void finishSpontaneousProcessing(String error) {
        processing = false;
        liveDone = true;
        processingRoomId = null;
        if (currentRoomId == null) {
            showRoomList();
        } else {
            if (sendButton != null) sendButton.setEnabled(true);
            updateTypingStatus();
            refreshMessages();
        }
        renderLiveBrainDialogIfOpen();
        if (error != null) showErrorDialog("自発送信判断に失敗しました。\n\n" + error, false);
    }

    private void finishProcessing(String roomId, String error, JSONObject failedUserMessage) {
        processing = false;
        liveDone = true;
        processingRoomId = null;
        if (currentRoomId == null) {
            showRoomList();
        } else {
            if (sendButton != null) sendButton.setEnabled(true);
            updateTypingStatus();
            if (roomId.equals(currentRoomId)) refreshMessages();
        }
        renderLiveBrainDialogIfOpen();
        if (error != null) {
            retryUserMessage = failedUserMessage;
            retryRoomId = roomId;
            showErrorDialog(error, true);
        }
    }

    private void retryFailedEvent() {
        if (processing || retryUserMessage == null || retryRoomId == null) return;
        String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            showErrorDialog("APIキーを読み出せません: " + error.getMessage(), false);
            return;
        }
        if (apiKey.isEmpty()) {
            showMissingApiKeyDialog();
            return;
        }
        JSONObject event = retryUserMessage;
        String room = retryRoomId;
        retryUserMessage = null;
        retryRoomId = null;
        startProcessingEvent(room, event, apiKey);
    }

    private void initializeLiveStages() {
        liveStages = new JSONArray();
        String[] ids = BrainEngine.stageIds();
        for (int i = 0; i < ids.length; i++) {
            JSONObject stage = new JSONObject();
            try {
                stage.put("stage_id", ids[i]);
                stage.put("stage_label", BrainEngine.stageLabel(ids[i]));
                stage.put("status", "waiting");
                stage.put("current", i + 1);
                stage.put("total", ids.length);
            } catch (Exception ignored) {
            }
            liveStages.put(stage);
        }
    }

    private void markStageStarted(String id, String label, int current, int total) {
        JSONObject stage = findLiveStage(id);
        if (stage == null) return;
        try {
            stage.put("stage_label", label);
            stage.put("status", "running");
            stage.put("current", current);
            stage.put("total", total);
        } catch (Exception ignored) {
        }
    }

    private void markStageCompleted(
            String id,
            String label,
            int current,
            int total,
            String summary,
            double confidence,
            JSONArray facts,
            String personalityEffect,
            String model,
            String effort
    ) {
        JSONObject stage = findLiveStage(id);
        if (stage == null) return;
        try {
            stage.put("stage_label", label);
            stage.put("status", "done");
            stage.put("current", current);
            stage.put("total", total);
            stage.put("summary", summary == null ? "" : summary);
            stage.put("confidence", confidence);
            stage.put("salient_facts", facts == null ? new JSONArray() : new JSONArray(facts.toString()));
            stage.put("personality_effect", personalityEffect == null ? "" : personalityEffect);
            stage.put("model", model);
            stage.put("reasoning_effort", effort);
        } catch (Exception ignored) {
        }
    }

    private JSONObject findLiveStage(String id) {
        for (int i = 0; i < liveStages.length(); i++) {
            JSONObject stage = liveStages.optJSONObject(i);
            if (stage != null && id.equals(stage.optString("stage_id"))) return stage;
        }
        return null;
    }

    private boolean isProcessingCurrentRoom() {
        return ConversationUiPolicy.showsProcessingInRoom(processing, currentRoomId, processingRoomId);
    }

    private void updateTypingStatus() {
        if (typingStatus == null) return;
        if (!isProcessingCurrentRoom()) {
            typingStatus.setVisibility(View.GONE);
            return;
        }
        typingStatus.setVisibility(View.VISIBLE);
        typingStatus.setText(liveNpcName + " が入力中⋯   タップして脳内を見る");
    }

    private void showLiveBrainDialog() {
        if (liveStages.length() == 0) initializeLiveStages();
        liveBrainContent = new LinearLayout(this);
        liveBrainContent.setOrientation(LinearLayout.VERTICAL);
        liveBrainContent.setPadding(dp(16), dp(6), dp(16), dp(18));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(liveBrainContent);
        liveBrainDialog = new AlertDialog.Builder(this)
                .setTitle(liveNpcName + " の脳内（リアルタイム）")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .create();
        liveBrainDialog.setOnDismissListener(dialog -> {
            liveBrainDialog = null;
            liveBrainContent = null;
        });
        liveBrainDialog.show();
        renderLiveBrainDialogIfOpen();
    }

    private void renderLiveBrainDialogIfOpen() {
        if (liveBrainDialog == null || liveBrainContent == null) return;
        liveBrainDialog.setTitle(liveNpcName + " の脳内（リアルタイム）");
        liveBrainContent.removeAllViews();

        TextView note = new TextView(this);
        note.setText(liveDone
                ? "この認知サイクルは完了しました。"
                : "各脳の公開用判断要約を処理と同時に更新しています。");
        note.setTextColor(Color.rgb(95, 95, 105));
        note.setTextSize(12);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        np.bottomMargin = dp(10);
        liveBrainContent.addView(note, np);

        for (int i = 0; i < liveStages.length(); i++) {
            JSONObject stage = liveStages.optJSONObject(i);
            if (stage != null) liveBrainContent.addView(createLiveStageCard(stage));
        }
    }

    private View createLiveStageCard(JSONObject stage) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(Color.rgb(247, 248, 250), Color.rgb(222, 224, 229), 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(7);
        card.setLayoutParams(cp);

        String status = stage.optString("status", "waiting");
        String statusText = "待機";
        if ("running".equals(status)) statusText = "思考中…";
        if ("done".equals(status)) statusText = "完了";

        TextView title = new TextView(this);
        title.setText(stage.optInt("current", 0) + "/" + stage.optInt("total", 10)
                + "  " + stage.optString("stage_label", "脳機能") + "  ·  " + statusText);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor("running".equals(status)
                ? Color.rgb(31, 93, 153)
                : Color.rgb(38, 38, 44));
        card.addView(title);

        if ("done".equals(status)) {
            TextView summary = new TextView(this);
            summary.setText(stage.optString("summary", "要約なし"));
            summary.setTextColor(Color.rgb(58, 58, 68));
            summary.setTextSize(13);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            sp.topMargin = dp(5);
            card.addView(summary, sp);

            String effect = stage.optString("personality_effect", "").trim();
            if (!effect.isEmpty()) {
                TextView e = new TextView(this);
                e.setText("人格影響: " + effect);
                e.setTextColor(Color.rgb(69, 79, 112));
                e.setTextSize(11);
                card.addView(e);
            }

            TextView meta = new TextView(this);
            int confidence = (int) Math.round(Math.max(0.0,
                    Math.min(1.0, stage.optDouble("confidence", 0.0))) * 100.0);
            meta.setText("信頼度 " + confidence + "% · "
                    + stage.optString("model", OpenAiClient.MODEL) + " / "
                    + ModelSettingsStore.displayLabel(stage.optString(
                            "reasoning_effort", modelSettingsStore.reasoningEffort())));
            meta.setTextColor(Color.rgb(120, 120, 130));
            meta.setTextSize(10);
            card.addView(meta);
        }
        return card;
    }

    private void showMessageDetails(JSONObject message) {
        JSONArray trace = message.optJSONArray("brain_trace");
        if (trace == null || trace.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("メッセージ詳細")
                    .setMessage("送信者: " + message.optString("sender_name", "あなた")
                            + "\n時刻: " + formatTime(message.optLong("time_ms", 0L))
                            + "\n\n" + message.optString("text", "")
                            + "\n\nユーザー入力なのでNPCの脳内トレースはありません。")
                    .setPositiveButton("閉じる", null)
                    .show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(18));
        boolean silentDecision = ConversationStore.isDebugDecisionSender(
                message.optString("sender_id", ""));
        if (!silentDecision) {
            TextView utterance = new TextView(this);
            utterance.setText("「" + message.optString("text", "") + "」");
            utterance.setTextSize(17);
            utterance.setTypeface(Typeface.DEFAULT_BOLD);
            content.addView(utterance);
        }
        for (int i = 0; i < trace.length(); i++) {
            JSONObject stage = trace.optJSONObject(i);
            if (stage != null) content.addView(createStoredTraceCard(stage, i + 1, trace.length()));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(message.optString("sender_name", "NPC") + " の脳内トレース")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private View createStoredTraceCard(JSONObject stage, int current, int total) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(Color.rgb(247, 248, 250), Color.rgb(222, 224, 229), 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(7);
        card.setLayoutParams(cp);

        TextView title = new TextView(this);
        title.setText(current + "/" + total + "  " + stage.optString("stage_label", "脳機能"));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView summary = new TextView(this);
        summary.setText(stage.optString("summary", "要約なし"));
        summary.setTextSize(13);
        summary.setTextColor(Color.rgb(58, 58, 68));
        card.addView(summary);

        String effect = stage.optString("personality_effect", "").trim();
        if (!effect.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("人格影響: " + effect);
            e.setTextSize(11);
            e.setTextColor(Color.rgb(69, 79, 112));
            card.addView(e);
        }

        JSONArray facts = stage.optJSONArray("salient_facts");
        if (facts != null && facts.length() > 0) {
            StringBuilder f = new StringBuilder("注目: ");
            for (int i = 0; i < Math.min(3, facts.length()); i++) {
                if (i > 0) f.append(" / ");
                f.append(facts.optString(i));
            }
            TextView fv = new TextView(this);
            fv.setText(f.toString());
            fv.setTextSize(11);
            fv.setTextColor(Color.rgb(88, 88, 98));
            card.addView(fv);
        }
        return card;
    }

    private void showHomeMenu(View anchor) {
        if (currentRoomId != null) return;
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("AI設定");
        popup.getMenu().add("NPC1 人格設定");
        popup.getMenu().add("NPC2 人格設定");
        popup.getMenu().add("NPC1 記憶を見る");
        popup.getMenu().add("NPC2 記憶を見る");
        popup.getMenu().add("会話履歴を消去");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("AI設定".equals(title)) showAiSettingsDialog();
            else if ("NPC1 人格設定".equals(title)) showPersonalityDialog("npc1");
            else if ("NPC2 人格設定".equals(title)) showPersonalityDialog("npc2");
            else if ("NPC1 記憶を見る".equals(title)) showMemoryDialog("npc1");
            else if ("NPC2 記憶を見る".equals(title)) showMemoryDialog("npc2");
            else if ("会話履歴を消去".equals(title)) confirmClearConversations();
            return true;
        });
        popup.show();
    }

    private void showAiSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(16));

        TextView model = new TextView(this);
        model.setText("モデル: gpt-5.6-luna");
        model.setTextSize(15);
        model.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(model);

        EditText keyInput = new EditText(this);
        keyInput.setHint(hasApiKey() ? "APIキー変更時のみ入力" : "sk-...");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(keyInput);

        TextView effortTitle = new TextView(this);
        effortTitle.setText("Luna 推論モード");
        effortTitle.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(effortTitle);

        RadioGroup efforts = new RadioGroup(this);
        String current = modelSettingsStore.reasoningEffort();
        for (String effort : ModelSettingsStore.supportedEfforts()) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(effort);
            option.setText(ModelSettingsStore.displayLabel(effort) + " — "
                    + ModelSettingsStore.description(effort));
            option.setChecked(effort.equals(current));
            efforts.addView(option);
        }
        form.addView(efforts);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new AlertDialog.Builder(this)
                .setTitle("AI設定")
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (!key.isEmpty()) {
                        try {
                            apiKeyStore.save(key);
                        } catch (Exception error) {
                            Toast.makeText(this, "APIキー保存失敗", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    int checked = efforts.getCheckedRadioButtonId();
                    View selected = efforts.findViewById(checked);
                    if (selected != null && selected.getTag() != null) {
                        modelSettingsStore.setReasoningEffort(selected.getTag().toString());
                    }
                    showRoomList();
                })
                .setNeutralButton("APIキー削除", (dialog, which) -> {
                    apiKeyStore.clear();
                    showRoomList();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showPersonalityDialog(String npcId) {
        CharacterStateStore character = demoRuntime.characterStore(npcId);
        MemoryStore memory = demoRuntime.memoryStore(npcId);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(16));

        EditText name = addTextField(form, "名前", demoRuntime.displayName(npcId), true);
        TraitControl extra = addTrait(form, "外向性", character.traitPercent(CharacterStateStore.extraversionKey()));
        TraitControl neuro = addTrait(form, "神経症傾向", character.traitPercent(CharacterStateStore.neuroticismKey()));
        TraitControl agree = addTrait(form, "協調性", character.traitPercent(CharacterStateStore.agreeablenessKey()));
        TraitControl consc = addTrait(form, "誠実性", character.traitPercent(CharacterStateStore.conscientiousnessKey()));
        TraitControl open = addTrait(form, "開放性", character.traitPercent(CharacterStateStore.opennessKey()));
        EditText role = addTextField(form, "役割・自己像", memory.profileText("role_identity"), false);
        EditText values = addTextField(form, "価値観", memory.profileText("value"), false);
        EditText goals = addTextField(form, "目標", memory.profileText("goal"), false);
        EditText fears = addTextField(form, "恐れ", memory.profileText("fear"), false);
        EditText relations = addTextField(form, "人間関係", memory.profileText("relationship"), false);
        EditText speech = addTextField(form, "話し方", character.speechStyle(), false);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new AlertDialog.Builder(this)
                .setTitle(demoRuntime.displayName(npcId) + " の人格")
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    character.saveProfile(
                            name.getText().toString(), extra.seek.getProgress(), neuro.seek.getProgress(),
                            agree.seek.getProgress(), consc.seek.getProgress(), open.seek.getProgress(),
                            speech.getText().toString());
                    memory.replaceProfileAdaptations(
                            role.getText().toString(), values.getText().toString(), goals.getText().toString(),
                            fears.getText().toString(), relations.getText().toString());
                    showRoomList();
                })
                .setNeutralButton("標準に戻す", (dialog, which) -> {
                    character.reset();
                    memory.clearProfileAdaptations();
                    showRoomList();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private EditText addTextField(LinearLayout parent, String label, String value, boolean oneLine) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(12);
        parent.addView(title);
        EditText field = new EditText(this);
        field.setText(value == null ? "" : value);
        field.setSingleLine(oneLine);
        if (!oneLine) {
            field.setMinLines(2);
            field.setMaxLines(4);
            field.setGravity(Gravity.TOP | Gravity.START);
        }
        parent.addView(field);
        return field;
    }

    private TraitControl addTrait(LinearLayout parent, String label, int value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(label + "  " + value);
        title.setTextSize(12);
        box.addView(title);
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(value);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                title.setText(label + "  " + p);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        box.addView(seek);
        parent.addView(box);
        return new TraitControl(box, seek);
    }

    private void showMemoryDialog(String npcId) {
        MemoryStore memory = demoRuntime.memoryStore(npcId);
        TextView text = new TextView(this);
        text.setText(memory.preview());
        text.setTextSize(14);
        text.setTextIsSelectable(true);
        text.setPadding(dp(18), dp(8), dp(18), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle(demoRuntime.displayName(npcId) + " の長期記憶")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void confirmClearConversations() {
        new AlertDialog.Builder(this)
                .setTitle("会話履歴を消去")
                .setMessage("NPCの長期記憶・人格・APIキーは消えません。")
                .setPositiveButton("消去", (dialog, which) -> {
                    conversationStore.clearAll();
                    showRoomList();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showMissingApiKeyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("APIキーが未設定です")
                .setMessage("APIキーはホーム右上の「AI設定」1か所だけで設定します。")
                .setPositiveButton("ホームへ", (dialog, which) -> showRoomList())
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void showErrorDialog(String message, boolean canRetry) {
        boolean network = message != null && (
                message.contains("DNS") || message.contains("ネットワーク")
                        || message.contains("接続") || message.contains("api.openai.com"));
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(network ? "通信エラー" : "処理エラー")
                .setMessage(message == null ? "不明なエラー" : message)
                .setNegativeButton("閉じる", null);
        if (canRetry && retryUserMessage != null) {
            builder.setPositiveButton("再試行", (dialog, which) -> retryFailedEvent());
        }
        builder.show();
    }

    private String firstNpcId(String roomId) {
        if (DemoRuntimeV032.ROOM_NPC2.equals(roomId)) return "npc2";
        return "npc1";
    }

    private boolean hasApiKey() {
        try {
            return !apiKeyStore.load().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String currentModelSummary() {
        return "GPT-5.6 Luna · reasoning "
                + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort())
                + " · APIキー " + (hasApiKey() ? "設定済み" : "未設定");
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.JAPAN).format(new Date(timeMs));
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureEdgeToEdgeWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void applySafeInsets(View root) {
        root.setPadding(dp(20), dp(12), dp(20), dp(8));
        if (Build.VERSION.SDK_INT >= 23) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        top = Math.max(top, cutout.getSafeInsetTop());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        right = Math.max(right, cutout.getSafeInsetRight());
                    }
                }
                view.setPadding(dp(20) + left, dp(12) + top, dp(20) + right, dp(8) + bottom);
                return insets;
            });
        }
    }
}
