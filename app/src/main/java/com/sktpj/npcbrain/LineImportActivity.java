package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class LineImportActivity extends Activity {
    private final Map<Integer, String> speakerByViewId = new HashMap<>();
    private final Map<Integer, String> npcByViewId = new HashMap<>();

    private SecureApiKeyStore apiKeyStore;
    private ModelSettingsStore modelSettingsStore;
    private LineChatImportParser.ParsedChat parsedChat;
    private RadioGroup speakerGroup;
    private RadioGroup npcGroup;
    private Button analyzeButton;
    private TextView statusView;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        setContentView(buildLoadingScreen());
        loadSharedChat();
    }

    private View buildLoadingScreen() {
        LinearLayout root = baseRoot();
        TextView title = title("LINEトークから人格を作成");
        root.addView(title);
        statusView = body("共有されたトーク履歴を読み込んでいます…");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(16);
        root.addView(statusView, sp);
        return wrap(root);
    }

    private void loadSharedChat() {
        new Thread(() -> {
            try {
                SharedText shared = readSharedText(getIntent());
                LineChatImportParser.ParsedChat parsed = LineChatImportParser.parse(
                        shared.displayName,
                        shared.text
                );
                if (parsed.messages.isEmpty()) {
                    throw new IllegalArgumentException("LINEのメッセージを読み取れませんでした。");
                }
                if (parsed.speakerNames.isEmpty()) {
                    throw new IllegalArgumentException("話者を検出できませんでした。");
                }
                parsedChat = parsed;
                runOnUiThread(() -> setContentView(buildReadyScreen(parsed)));
            } catch (Exception error) {
                runOnUiThread(() -> showLoadError(error));
            }
        }, "line-import-read").start();
    }

    private View buildReadyScreen(LineChatImportParser.ParsedChat chat) {
        LinearLayout root = baseRoot();
        root.addView(title("LINEトークから人格を作成"));

        TextView source = body(
                "ファイル: " + safe(chat.sourceTitle, "共有されたトーク履歴")
                        + "\nメッセージ: " + chat.messages.size() + "件"
        );
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sourceParams.topMargin = dp(12);
        root.addView(source, sourceParams);

        TextView speakerLabel = section("解析する人物");
        root.addView(speakerLabel, topMargin(20));
        speakerGroup = new RadioGroup(this);
        speakerGroup.setOrientation(RadioGroup.VERTICAL);
        speakerByViewId.clear();
        String suggested = LineChatImportParser.resolveSuggestedSpeaker(chat);
        for (String speaker : chat.speakerNames) {
            RadioButton button = new RadioButton(this);
            int id = View.generateViewId();
            button.setId(id);
            button.setText(speaker);
            button.setTextSize(16);
            speakerByViewId.put(id, speaker);
            speakerGroup.addView(button);
            if (speaker.equals(suggested)) button.setChecked(true);
        }
        root.addView(speakerGroup);

        TextView npcLabel = section("人格を設定するNPC");
        root.addView(npcLabel, topMargin(18));
        npcGroup = new RadioGroup(this);
        npcGroup.setOrientation(RadioGroup.VERTICAL);
        npcByViewId.clear();
        addNpcOption("npc1", "NPC1");
        addNpcOption("npc2", "NPC2");
        root.addView(npcGroup);

        statusView = body("選択した人物の発話だけを解析し、人格設定へ反映します。トーク本文は会話履歴や長期記憶へ保存しません。");
        LinearLayout.LayoutParams statusParams = topMargin(18);
        root.addView(statusView, statusParams);

        analyzeButton = new Button(this);
        analyzeButton.setText("解析して人格に設定");
        analyzeButton.setTextSize(15);
        analyzeButton.setTypeface(Typeface.DEFAULT_BOLD);
        analyzeButton.setOnClickListener(v -> analyzeAndSave());
        LinearLayout.LayoutParams buttonParams = topMargin(18);
        buttonParams.height = dp(52);
        root.addView(analyzeButton, buttonParams);

        Button cancel = new Button(this);
        cancel.setText("キャンセル");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, topMargin(8));
        return wrap(root);
    }

    private void addNpcOption(String npcId, String slotLabel) {
        CharacterStateStore store = characterStore(npcId);
        String currentName = store.displayName();
        String label = slotLabel;
        if (currentName != null && !currentName.trim().isEmpty() && !"NPC".equals(currentName.trim())) {
            label += "（現在: " + currentName.trim() + "）";
        }
        RadioButton button = new RadioButton(this);
        int id = View.generateViewId();
        button.setId(id);
        button.setText(label);
        button.setTextSize(16);
        npcByViewId.put(id, npcId);
        npcGroup.addView(button);
        if ("npc1".equals(npcId)) button.setChecked(true);
    }

    private void analyzeAndSave() {
        if (busy || parsedChat == null) return;
        String speaker = speakerByViewId.get(speakerGroup.getCheckedRadioButtonId());
        String npcId = npcByViewId.get(npcGroup.getCheckedRadioButtonId());
        if (speaker == null || speaker.trim().isEmpty()) {
            showMessage("解析する人物を選択してください。");
            return;
        }
        if (npcId == null || npcId.trim().isEmpty()) {
            showMessage("人格を設定するNPCを選択してください。");
            return;
        }

        final String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            showMessage("APIキーを読み出せません: " + safeError(error));
            return;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showMissingApiKeyDialog();
            return;
        }

        setBusy(true, speaker + " の人格を解析しています…");
        new Thread(() -> {
            try {
                ImportedPersonalityProfile profile = PersonalityImportAnalyzer.analyze(
                        getApplicationContext(),
                        apiKey,
                        modelSettingsStore.reasoningEffort(),
                        parsedChat,
                        speaker
                );
                characterStore(npcId).saveProfile(
                        profile.name,
                        profile.extraversion,
                        profile.neuroticism,
                        profile.agreeableness,
                        profile.conscientiousness,
                        profile.openness,
                        profile.speechStyle
                );
                runOnUiThread(() -> {
                    setBusy(false, "人格を設定しました。");
                    showSuccessDialog(npcId, profile);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false, "解析に失敗しました。人格は変更していません。");
                    showMessage("解析に失敗しました。人格は変更していません。\n\n" + safeError(error));
                });
            }
        }, "line-personality-analysis").start();
    }

    private void showSuccessDialog(String npcId, ImportedPersonalityProfile profile) {
        String slot = "npc2".equals(npcId) ? "NPC2" : "NPC1";
        new AlertDialog.Builder(this)
                .setTitle("人格を設定しました")
                .setMessage(slot + " に " + profile.name + " の人格を設定しました。\n\n"
                        + "外向性 " + profile.extraversion
                        + " / 神経症傾向 " + profile.neuroticism
                        + " / 協調性 " + profile.agreeableness
                        + " / 誠実性 " + profile.conscientiousness
                        + " / 開放性 " + profile.openness)
                .setPositiveButton("NPCBrainを開く", (dialog, which) -> openNpcBrain())
                .setNegativeButton("閉じる", (dialog, which) -> finish())
                .show();
    }

    private void showMissingApiKeyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("OpenAI APIキーが必要です")
                .setMessage("NPCBrainのホーム → AI設定でAPIキーを設定してから、LINEのトーク履歴をもう一度共有してください。")
                .setPositiveButton("NPCBrainを開く", (dialog, which) -> openNpcBrain())
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void openNpcBrain() {
        Intent intent = new Intent(this, DemoActivityV032.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showLoadError(Exception error) {
        LinearLayout root = baseRoot();
        root.addView(title("LINEトークを読み込めませんでした"));
        TextView message = body(safeError(error));
        root.addView(message, topMargin(16));
        Button close = new Button(this);
        close.setText("閉じる");
        close.setOnClickListener(v -> finish());
        root.addView(close, topMargin(20));
        setContentView(wrap(root));
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        if (analyzeButton != null) analyzeButton.setEnabled(!value);
        if (speakerGroup != null) setRadioGroupEnabled(speakerGroup, !value);
        if (npcGroup != null) setRadioGroupEnabled(npcGroup, !value);
        if (statusView != null) statusView.setText(message);
    }

    private static void setRadioGroupEnabled(RadioGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
    }

    private CharacterStateStore characterStore(String npcId) {
        Context context = "npc2".equals(npcId)
                ? new NpcStorageContext(getApplicationContext(), "npc2")
                : getApplicationContext();
        return new CharacterStateStore(context);
    }

    private SharedText readSharedText(Intent intent) throws Exception {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            throw new IllegalArgumentException("LINEの共有から開いてください。");
        }

        Uri uri = null;
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) uri = (Uri) stream;
        if (uri == null) {
            ClipData clipData = intent.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                uri = clipData.getItemAt(0).getUri();
            }
        }
        if (uri != null) {
            String displayName = queryDisplayName(uri);
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("共有ファイルを開けませんでした。");
                return new SharedText(displayName, readAll(input));
            }
        }

        CharSequence extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (extraText != null && extraText.length() > 0) {
            return new SharedText("LINE共有テキスト", extraText.toString());
        }
        throw new IllegalArgumentException("共有されたトーク履歴ファイルを取得できませんでした。");
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value.trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment() == null ? "LINEトーク履歴.txt" : uri.getLastPathSegment();
    }

    private static String readAll(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (result.length() > 0) result.append('\n');
                result.append(line);
            }
        }
        return result.toString();
    }

    private View wrap(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(248, 249, 251));
        root.setFitsSystemWindows(true);
        return root;
    }

    private TextView title(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(24, 24, 28));
        view.setTextSize(22);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView section(String value) {
        TextView view = body(value);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView body(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(70, 70, 80));
        view.setTextSize(14);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(dp);
        return params;
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String safeError(Throwable error) {
        if (error == null) return "不明なエラーです。";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message.trim();
    }

    private static final class SharedText {
        final String displayName;
        final String text;

        SharedText(String displayName, String text) {
            this.displayName = displayName == null ? "" : displayName;
            this.text = text == null ? "" : text;
        }
    }
}
