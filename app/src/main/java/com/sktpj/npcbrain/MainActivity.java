package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final class TraitControl {
        final LinearLayout root;
        final SeekBar seekBar;

        TraitControl(LinearLayout root, SeekBar seekBar) {
            this.root = root;
            this.seekBar = seekBar;
        }
    }

    private SecureApiKeyStore apiKeyStore;
    private ConversationStore conversationStore;
    private DemoRuntime demoRuntime;

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
    private TextView processingStatus;
    private volatile boolean processing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        apiKeyStore = new SecureApiKeyStore(this);
        conversationStore = new ConversationStore(this);
        demoRuntime = new DemoRuntime(this, conversationStore);
        setContentView(buildShell());
        showRoomList();
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
        LinearLayout.LayoutParams titleBoxParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleBoxParams.leftMargin = dp(4);
        header.addView(titleBox, titleBoxParams);

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
        menuButton.setContentDescription("NPCBrainデモ設定");
        menuButton.setOnClickListener(v -> showMenu(menuButton));
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
        hideKeyboard();
        backButton.setVisibility(View.INVISIBLE);
        titleView.setText("NPCBrain Demo");
        subtitleView.setText("会話をタップして、その発話を作った脳内トレースを確認");
        screenContainer.removeAllViews();

        TextView model = new TextView(this);
        model.setText("GPT-5.6 Luna · reasoning MAX");
        model.setTextColor(Color.rgb(52, 67, 101));
        model.setTextSize(12);
        model.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modelParams.topMargin = dp(4);
        screenContainer.addView(model, modelParams);

        TextView note = new TextView(this);
        note.setText("この画面はライブラリの挙動確認用デモです。NPC1/NPC2は人格と長期記憶を別々に持ちます。自律生活・時間経過は次段階で追加します。");
        note.setTextColor(Color.rgb(92, 92, 102));
        note.setTextSize(12);
        note.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(6);
        noteParams.bottomMargin = dp(12);
        screenContainer.addView(note, noteParams);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rooms = new LinearLayout(this);
        rooms.setOrientation(LinearLayout.VERTICAL);
        rooms.setPadding(0, 0, 0, dp(24));
        for (String roomId : demoRuntime.roomIds()) {
            rooms.addView(createRoomRow(roomId));
        }
        scroll.addView(rooms, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        screenContainer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View createRoomRow(String roomId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setMinimumHeight(dp(76));
        row.setBackground(cardBackground(
                Color.rgb(255, 255, 255), Color.rgb(225, 227, 232), 14));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> openRoom(roomId));

        TextView title = new TextView(this);
        title.setText(demoRuntime.roomTitle(roomId));
        title.setTextColor(Color.rgb(32, 32, 38));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title);

        JSONObject last = conversationStore.lastMessage(roomId);
        String preview = "まだ会話はありません";
        if (last != null) {
            String sender = last.optString("sender_name", "");
            String text = last.optString("text", "");
            preview = sender + (sender.isEmpty() ? "" : ": ") + text;
        }
        TextView lastText = new TextView(this);
        lastText.setText(preview);
        lastText.setTextColor(Color.rgb(102, 102, 112));
        lastText.setTextSize(13);
        lastText.setMaxLines(1);
        lastText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        previewParams.topMargin = dp(5);
        row.addView(lastText, previewParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);
        return row;
    }

    private void openRoom(String roomId) {
        currentRoomId = roomId;
        backButton.setVisibility(View.VISIBLE);
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
        chatScroll.addView(messageContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        screenContainer.addView(chatScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        processingStatus = new TextView(this);
        processingStatus.setText(processing ? "Luna MAXで処理中…" : "");
        processingStatus.setTextColor(Color.rgb(92, 92, 102));
        processingStatus.setTextSize(11);
        processingStatus.setGravity(Gravity.CENTER_VERTICAL);
        processingStatus.setMinHeight(dp(22));
        screenContainer.addView(processingStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.addView(messageInput, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new Button(this);
        sendButton.setText("送信");
        sendButton.setTextSize(13);
        sendButton.setTypeface(Typeface.DEFAULT_BOLD);
        sendButton.setContentDescription("メッセージを送信");
        sendButton.setEnabled(!processing);
        sendButton.setOnClickListener(v -> sendCurrentMessage());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(72), dp(52));
        sendParams.leftMargin = dp(6);
        composer.addView(sendButton, sendParams);
        screenContainer.addView(composer);

        refreshMessages();
    }

    private void refreshMessages() {
        if (currentRoomId == null || messageContainer == null) return;
        messageContainer.removeAllViews();
        JSONArray messages = conversationStore.messages(currentRoomId);
        if (messages.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("まだメッセージはありません。\n送信すると、そのイベントをNPCの脳が処理します。");
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
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private View createMessageView(JSONObject message) {
        boolean isUser = "user".equals(message.optString("sender_id"));
        JSONArray trace = message.optJSONArray("brain_trace");
        boolean hasTrace = trace != null && trace.length() > 0;

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isUser ? Gravity.END : Gravity.START);
        wrapper.setPadding(0, dp(4), 0, dp(4));

        if (!isUser) {
            TextView sender = new TextView(this);
            sender.setText(message.optString("sender_name", "NPC"));
            sender.setTextColor(Color.rgb(92, 92, 102));
            sender.setTextSize(11);
            LinearLayout.LayoutParams senderParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            senderParams.leftMargin = dp(8);
            wrapper.addView(sender, senderParams);
        }

        TextView bubble = new TextView(this);
        bubble.setText(message.optString("text", ""));
        bubble.setTextSize(16);
        bubble.setTextColor(Color.rgb(28, 28, 32));
        bubble.setLineSpacing(0f, 1.15f);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setMaxWidth(dp(310));
        bubble.setBackground(cardBackground(
                isUser ? Color.rgb(211, 241, 204) : Color.rgb(255, 255, 255),
                isUser ? Color.rgb(181, 221, 172) : Color.rgb(224, 226, 231),
                16));
        bubble.setClickable(true);
        bubble.setFocusable(true);
        bubble.setContentDescription(hasTrace
                ? "メッセージ。タップして脳内トレースを表示"
                : "メッセージ詳細");
        bubble.setOnClickListener(v -> showMessageDetails(message));
        wrapper.addView(bubble, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView meta = new TextView(this);
        String metaText = formatTime(message.optLong("time_ms", 0L));
        if (hasTrace) metaText += " · 脳内を見る";
        meta.setText(metaText);
        meta.setTextColor(Color.rgb(130, 130, 140));
        meta.setTextSize(10);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(2);
        metaParams.leftMargin = dp(8);
        metaParams.rightMargin = dp(8);
        wrapper.addView(meta, metaParams);

        return wrapper;
    }

    private void showMessageDetails(JSONObject message) {
        JSONArray trace = message.optJSONArray("brain_trace");
        boolean hasTrace = trace != null && trace.length() > 0;
        if (!hasTrace) {
            String details = "送信者: " + message.optString("sender_name", "あなた")
                    + "\n時刻: " + formatTime(message.optLong("time_ms", 0L))
                    + "\n\n" + message.optString("text", "")
                    + "\n\nこれはユーザー入力イベントなのでNPCの脳内トレースはありません。";
            new AlertDialog.Builder(this)
                    .setTitle("メッセージ詳細")
                    .setMessage(details)
                    .setPositiveButton("閉じる", null)
                    .show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(18));

        TextView messageText = new TextView(this);
        messageText.setText("「" + message.optString("text", "") + "」");
        messageText.setTextColor(Color.rgb(28, 28, 34));
        messageText.setTextSize(17);
        messageText.setTypeface(Typeface.DEFAULT_BOLD);
        messageText.setTextIsSelectable(true);
        content.addView(messageText);

        String action = message.optString("action", "").trim();
        if (!action.isEmpty()) {
            TextView actionView = new TextView(this);
            actionView.setText("行動: " + action);
            actionView.setTextColor(Color.rgb(78, 78, 88));
            actionView.setTextSize(13);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            actionParams.topMargin = dp(6);
            content.addView(actionView, actionParams);
        }

        TextView note = new TextView(this);
        note.setText("この発話を生成した10段階の公開用判断記録です。逐語的な内部推論ではありません。");
        note.setTextColor(Color.rgb(108, 108, 118));
        note.setTextSize(11);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(8);
        noteParams.bottomMargin = dp(8);
        content.addView(note, noteParams);

        for (int i = 0; i < trace.length(); i++) {
            JSONObject stage = trace.optJSONObject(i);
            if (stage != null) content.addView(createTraceCard(stage, i + 1, trace.length()));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(message.optString("sender_name", "NPC") + " の脳内トレース")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private View createTraceCard(JSONObject stage, int current, int total) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(
                Color.rgb(247, 248, 250), Color.rgb(222, 224, 229), 12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(7);
        card.setLayoutParams(cardParams);

        TextView title = new TextView(this);
        title.setText(current + "/" + total + "  "
                + stage.optString("stage_label", stage.optString("stage_id", "脳機能")));
        title.setTextColor(Color.rgb(38, 38, 44));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView summary = new TextView(this);
        summary.setText(stage.optString("summary", "要約なし"));
        summary.setTextColor(Color.rgb(58, 58, 68));
        summary.setTextSize(13);
        summary.setLineSpacing(0f, 1.13f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(5);
        card.addView(summary, summaryParams);

        String effect = stage.optString("personality_effect", "").trim();
        if (!effect.isEmpty()) {
            TextView effectView = new TextView(this);
            effectView.setText("人格影響: " + effect);
            effectView.setTextColor(Color.rgb(69, 79, 112));
            effectView.setTextSize(12);
            LinearLayout.LayoutParams effectParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            effectParams.topMargin = dp(5);
            card.addView(effectView, effectParams);
        }

        JSONArray facts = stage.optJSONArray("salient_facts");
        if (facts != null && facts.length() > 0) {
            StringBuilder text = new StringBuilder("注目: ");
            int count = Math.min(3, facts.length());
            for (int i = 0; i < count; i++) {
                String fact = facts.optString(i, "").trim();
                if (fact.isEmpty()) continue;
                if (text.length() > 4) text.append(" / ");
                text.append(fact);
            }
            TextView factsView = new TextView(this);
            factsView.setText(text.toString());
            factsView.setTextColor(Color.rgb(88, 88, 98));
            factsView.setTextSize(11);
            LinearLayout.LayoutParams factsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            factsParams.topMargin = dp(5);
            card.addView(factsView, factsParams);
        }

        int confidence = (int) Math.round(
                Math.max(0.0, Math.min(1.0, stage.optDouble("confidence", 0.0))) * 100.0);
        TextView meta = new TextView(this);
        meta.setText("信頼度 " + confidence + "% · Luna MAX");
        meta.setTextColor(Color.rgb(120, 120, 130));
        meta.setTextSize(10);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(5);
        card.addView(meta, metaParams);
        return card;
    }

    private void sendCurrentMessage() {
        if (processing || currentRoomId == null || messageInput == null) return;
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        final String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            showErrorDialog("APIキーを読み出せません: " + error.getMessage());
            return;
        }
        if (apiKey.isEmpty()) {
            showApiKeyDialog();
            return;
        }

        hideKeyboard();
        JSONObject userMessage = conversationStore.appendUserMessage(
                currentRoomId, text, System.currentTimeMillis());
        messageInput.setText("");
        processing = true;
        sendButton.setEnabled(false);
        processingStatus.setText("NPCがメッセージを確認しています…");
        refreshMessages();

        String roomAtStart = currentRoomId;
        new Thread(() -> {
            try {
                demoRuntime.processUserMessage(
                        roomAtStart,
                        userMessage,
                        apiKey,
                        new DemoRuntime.Listener() {
                            @Override
                            public void onNpcStarted(
                                    String npcId,
                                    String displayName,
                                    int current,
                                    int total
                            ) {
                                runOnUiThread(() -> {
                                    if (processingStatus != null && roomAtStart.equals(currentRoomId)) {
                                        processingStatus.setText(
                                                current + "/" + total + "  " + displayName + " が考えています · Luna MAX");
                                    }
                                });
                            }

                            @Override
                            public void onNpcFinished(
                                    String npcId,
                                    String displayName,
                                    boolean sentMessage
                            ) {
                                runOnUiThread(() -> {
                                    if (roomAtStart.equals(currentRoomId)) refreshMessages();
                                });
                            }
                        }
                );
                runOnUiThread(() -> finishProcessing(roomAtStart, null));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                runOnUiThread(() -> finishProcessing(roomAtStart, message));
            }
        }, "npcbrain-demo-message").start();
    }

    private void finishProcessing(String roomId, String error) {
        processing = false;
        if (roomId.equals(currentRoomId)) {
            if (sendButton != null) sendButton.setEnabled(true);
            if (processingStatus != null) {
                processingStatus.setText(error == null ? "" : "処理エラー");
            }
            refreshMessages();
        }
        if (error != null) showErrorDialog(error);
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("NPC1 人格設定");
        popup.getMenu().add("NPC2 人格設定");
        popup.getMenu().add("NPC1 記憶を見る");
        popup.getMenu().add("NPC2 記憶を見る");
        popup.getMenu().add("OpenAI APIキー");
        popup.getMenu().add("会話履歴を消去");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("NPC1 人格設定".equals(title)) {
                showPersonalityDialog("npc1");
            } else if ("NPC2 人格設定".equals(title)) {
                showPersonalityDialog("npc2");
            } else if ("NPC1 記憶を見る".equals(title)) {
                showMemoryDialog("npc1");
            } else if ("NPC2 記憶を見る".equals(title)) {
                showMemoryDialog("npc2");
            } else if ("OpenAI APIキー".equals(title)) {
                showApiKeyDialog();
            } else if ("会話履歴を消去".equals(title)) {
                confirmClearConversations();
            }
            return true;
        });
        popup.show();
    }

    private void showPersonalityDialog(String npcId) {
        CharacterStateStore characterStore = demoRuntime.characterStore(npcId);
        MemoryStore memoryStore = demoRuntime.memoryStore(npcId);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(16));

        EditText name = addTextField(
                form, "名前", demoRuntime.displayName(npcId), true, 1);
        EditText role = addTextField(
                form, "役割・自己像", memoryStore.profileText("role_identity"), false, 2);

        TextView traitHelp = new TextView(this);
        traitHelp.setText("Big Five（0=低い / 100=高い）");
        traitHelp.setTextSize(13);
        traitHelp.setTextColor(Color.rgb(78, 78, 88));
        LinearLayout.LayoutParams traitHelpParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        traitHelpParams.topMargin = dp(12);
        form.addView(traitHelp, traitHelpParams);

        TraitControl extraversion = createTraitControl(
                "外向性",
                characterStore.traitPercent(CharacterStateStore.extraversionKey()));
        TraitControl neuroticism = createTraitControl(
                "神経症傾向",
                characterStore.traitPercent(CharacterStateStore.neuroticismKey()));
        TraitControl agreeableness = createTraitControl(
                "協調性",
                characterStore.traitPercent(CharacterStateStore.agreeablenessKey()));
        TraitControl conscientiousness = createTraitControl(
                "誠実性",
                characterStore.traitPercent(CharacterStateStore.conscientiousnessKey()));
        TraitControl openness = createTraitControl(
                "開放性",
                characterStore.traitPercent(CharacterStateStore.opennessKey()));

        form.addView(extraversion.root);
        form.addView(neuroticism.root);
        form.addView(agreeableness.root);
        form.addView(conscientiousness.root);
        form.addView(openness.root);

        EditText values = addTextField(
                form, "価値観", memoryStore.profileText("value"), false, 2);
        EditText goals = addTextField(
                form, "目標", memoryStore.profileText("goal"), false, 2);
        EditText fears = addTextField(
                form, "恐れ・避けたいこと", memoryStore.profileText("fear"), false, 2);
        EditText relationships = addTextField(
                form, "人間関係・相手への態度", memoryStore.profileText("relationship"), false, 3);
        EditText speechStyle = addTextField(
                form, "話し方", characterStore.speechStyle(), false, 2);

        TextView currentState = new TextView(this);
        currentState.setText("現在状態: " + characterStore.dynamicStateSummary());
        currentState.setTextSize(12);
        currentState.setTextColor(Color.rgb(96, 96, 106));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stateParams.topMargin = dp(12);
        form.addView(currentState, stateParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(demoRuntime.displayName(npcId) + " の人格")
                .setMessage("NPCごとに人格・長期記憶を分離します。脳の9専門領域＋Global Workspaceは増減しません。")
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    characterStore.saveProfile(
                            name.getText().toString(),
                            extraversion.seekBar.getProgress(),
                            neuroticism.seekBar.getProgress(),
                            agreeableness.seekBar.getProgress(),
                            conscientiousness.seekBar.getProgress(),
                            openness.seekBar.getProgress(),
                            speechStyle.getText().toString()
                    );
                    memoryStore.replaceProfileAdaptations(
                            role.getText().toString(),
                            values.getText().toString(),
                            goals.getText().toString(),
                            fears.getText().toString(),
                            relationships.getText().toString()
                    );
                    refreshCurrentScreenTitles();
                    Toast.makeText(this, "人格設定を保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("標準に戻す", (dialog, which) -> {
                    characterStore.reset();
                    memoryStore.clearProfileAdaptations();
                    refreshCurrentScreenTitles();
                    Toast.makeText(this, "人格設定を標準に戻しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void refreshCurrentScreenTitles() {
        if (currentRoomId == null) {
            showRoomList();
        } else {
            titleView.setText(demoRuntime.roomTitle(currentRoomId));
            subtitleView.setText(demoRuntime.roomSubtitle(currentRoomId));
            refreshMessages();
        }
    }

    private EditText addTextField(
            LinearLayout parent,
            String label,
            String value,
            boolean singleLine,
            int minLines
    ) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(13);
        title.setTextColor(Color.rgb(58, 58, 66));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(10);
        parent.addView(title, titleParams);

        EditText field = new EditText(this);
        field.setText(value == null ? "" : value);
        field.setTextSize(15);
        field.setSingleLine(singleLine);
        if (!singleLine) {
            field.setMinLines(Math.max(2, minLines));
            field.setMaxLines(Math.max(4, minLines + 2));
            field.setGravity(Gravity.TOP | Gravity.START);
            field.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        parent.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return field;
    }

    private TraitControl createTraitControl(String label, int progress) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(5), 0, 0);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(13);
        name.setTextColor(Color.rgb(58, 58, 66));
        labelRow.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setText(Integer.toString(progress));
        value.setTextSize(12);
        value.setTextColor(Color.rgb(92, 92, 102));
        labelRow.addView(value);
        root.addView(labelRow);

        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(Math.max(0, Math.min(100, progress)));
        seek.setContentDescription(label + "の値");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int valueNow, boolean fromUser) {
                value.setText(Integer.toString(valueNow));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(seek, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        return new TraitControl(root, seek);
    }

    private void showMemoryDialog(String npcId) {
        MemoryStore memoryStore = demoRuntime.memoryStore(npcId);
        TextView memoryText = new TextView(this);
        memoryText.setText(memoryStore.preview());
        memoryText.setTextSize(14);
        memoryText.setTextColor(Color.rgb(32, 32, 38));
        memoryText.setTextIsSelectable(true);
        memoryText.setPadding(dp(18), dp(8), dp(18), dp(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(memoryText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(demoRuntime.displayName(npcId) + " の長期記憶")
                .setMessage("このNPCだけのエピソード記憶・意味記憶です。")
                .setView(scroll)
                .setNeutralButton("学習記憶を消去", (dialog, which) -> {
                    memoryStore.clear();
                    Toast.makeText(this, "学習した長期記憶を消去しました", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void showApiKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setSingleLine(true);
        keyInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(keyInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("OpenAI APIキー")
                .setMessage("全NPCの認知処理はGPT-5.6 Luna / reasoning MAXを使用します。APIキーはAndroid Keystoreで暗号化して保存します。")
                .setView(box)
                .setPositiveButton("保存", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (key.isEmpty()) {
                        Toast.makeText(this, "APIキーが空です", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        apiKeyStore.save(key);
                        Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show();
                    } catch (Exception error) {
                        Toast.makeText(this,
                                "保存失敗: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("削除", (dialog, which) -> {
                    apiKeyStore.clear();
                    Toast.makeText(this, "APIキーを削除しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void confirmClearConversations() {
        new AlertDialog.Builder(this)
                .setTitle("会話履歴を消去")
                .setMessage("3つのデモトークと、各NPC発話に紐づく脳内トレースを削除します。NPCの人格・長期記憶・APIキーは削除しません。")
                .setPositiveButton("消去", (dialog, which) -> {
                    conversationStore.clearAll();
                    if (currentRoomId == null) showRoomList(); else refreshMessages();
                    Toast.makeText(this, "会話履歴を消去しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private GradientDrawable cardBackground(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void configureEdgeToEdgeWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void applySafeInsets(View root) {
        final int horizontal = dp(12);
        final int topBase = dp(4);
        final int bottomBase = dp(8);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();

            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }

            view.setPadding(
                    horizontal + left,
                    topBase + top,
                    horizontal + right,
                    bottomBase + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void hideKeyboard() {
        View current = getCurrentFocus();
        if (current == null) return;
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(current.getWindowToken(), 0);
        }
        current.clearFocus();
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("処理エラー")
                .setMessage(message == null ? "不明なエラー" : message)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private static String formatTime(long timeMs) {
        if (timeMs <= 0L) return "";
        return new SimpleDateFormat("HH:mm", Locale.JAPAN).format(new Date(timeMs));
    }

    @Override
    public void onBackPressed() {
        if (currentRoomId != null) {
            showRoomList();
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
