package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private static final String NEW_NPC = "__new_npc__";

    private final Map<Integer, String> speakerByViewId = new HashMap<>();
    private final Map<Integer, String> npcByViewId = new HashMap<>();

    private SecureApiKeyStore apiKeyStore;
    private ModelSettingsStore modelSettingsStore;
    private NpcRegistryStore npcRegistry;
    private LineChatImportParser.ParsedChat parsedChat;
    private RadioGroup speakerGroup;
    private RadioGroup npcGroup;
    private Button analyzeButton;
    private TextView statusView;
    private volatile boolean busy;
    private volatile boolean cancelled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        npcRegistry = new NpcRegistryStore(this);
        setContentView(buildLoadingScreen());
        loadSharedChat();
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
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
                if (cancelled) return;
                parsedChat = parsed;
                runOnUiThread(() -> {
                    if (!cancelled) setContentView(buildReadyScreen(parsed));
                });
            } catch (Exception error) {
                if (cancelled) return;
                runOnUiThread(() -> {
                    if (!cancelled) showLoadError(error);
                });
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

        TextView npcLabel = section("保存先");
        root.addView(npcLabel, topMargin(18));
        npcGroup = new RadioGroup(this);
        npcGroup.setOrientation(RadioGroup.VERTICAL);
        npcByViewId.clear();
        addNpcOption(NEW_NPC, "新しいNPCとして追加", true);
        for (String npcId : npcRegistry.activeNpcIds()) {
            CharacterStateStore store = characterStore(npcId);
            addNpcOption(npcId, store.displayName() + "（" + npcId + "）", false);
        }
        root.addView(npcGroup);

        statusView = body("人格に加えて、関係・年齢・経歴も会話内容から推定します。根拠がない項目は既定値を入れ、保存前に確認・編集できます。トーク本文は会話履歴や長期記憶へ保存しません。");
        LinearLayout.LayoutParams statusParams = topMargin(18);
        root.addView(statusView, statusParams);

        analyzeButton = new Button(this);
        analyzeButton.setText("解析して確認");
        analyzeButton.setTextSize(15);
        analyzeButton.setTypeface(Typeface.DEFAULT_BOLD);
        analyzeButton.setOnClickListener(v -> analyzeAndConfirm());
        LinearLayout.LayoutParams buttonParams = topMargin(18);
        buttonParams.height = dp(52);
        root.addView(analyzeButton, buttonParams);

        Button cancel = new Button(this);
        cancel.setText("キャンセル");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, topMargin(8));
        return wrap(root);
    }

    private void addNpcOption(String npcId, String label, boolean checked) {
        RadioButton button = new RadioButton(this);
        int id = View.generateViewId();
        button.setId(id);
        button.setText(label);
        button.setTextSize(16);
        npcByViewId.put(id, npcId);
        npcGroup.addView(button);
        if (checked) button.setChecked(true);
    }

    private void analyzeAndConfirm() {
        if (busy || parsedChat == null) return;
        String speaker = speakerByViewId.get(speakerGroup.getCheckedRadioButtonId());
        String npcSelection = npcByViewId.get(npcGroup.getCheckedRadioButtonId());
        if (speaker == null || speaker.trim().isEmpty()) {
            showMessage("解析する人物を選択してください。");
            return;
        }
        if (npcSelection == null || npcSelection.trim().isEmpty()) {
            showMessage("保存先を選択してください。");
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

        setBusy(true, speaker + " の人格と初期プロフィールを解析しています…");
        new Thread(() -> {
            try {
                ImportedPersonalityProfile profile = PersonalityImportAnalyzer.analyze(
                        getApplicationContext(),
                        apiKey,
                        modelSettingsStore.reasoningEffort(),
                        parsedChat,
                        speaker
                );
                if (cancelled) return;
                runOnUiThread(() -> {
                    if (cancelled) return;
                    setBusy(false, "解析しました。内容を確認して保存してください。");
                    showProfileConfirmation(npcSelection, profile);
                });
            } catch (Exception error) {
                if (cancelled) return;
                runOnUiThread(() -> {
                    if (cancelled) return;
                    setBusy(false, "解析に失敗しました。人格は変更していません。");
                    showMessage("解析に失敗しました。人格は変更していません。\n\n" + safeError(error));
                });
            }
        }, "line-personality-analysis").start();
    }

    private void showProfileConfirmation(String npcSelection, ImportedPersonalityProfile profile) {
        boolean createNew = NEW_NPC.equals(npcSelection);
        CharacterStateStore existing = createNew ? null : characterStore(npcSelection);
        boolean metadataEditable = createNew || (existing != null && !existing.identityMetadataLocked());

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);

        EditText name = input("名前", profile.name, true);
        EditText relationship = input("ユーザーとの関係",
                metadataEditable ? profile.relationshipToUser : existing.relationshipToUser(), metadataEditable);
        EditText age = input("年齢",
                metadataEditable ? profile.age : existing.age(), metadataEditable);
        EditText background = input("経歴",
                metadataEditable ? profile.background : existing.background(), metadataEditable);
        form.addView(name);
        form.addView(relationship);
        form.addView(age);
        form.addView(background);

        TextView traits = body("外向性 " + profile.extraversion
                + " / 神経症傾向 " + profile.neuroticism
                + " / 協調性 " + profile.agreeableness
                + " / 誠実性 " + profile.conscientiousness
                + " / 開放性 " + profile.openness
                + "\n話し方: " + profile.speechStyle);
        form.addView(traits, topMargin(12));

        String note = metadataEditable
                ? "関係・年齢・経歴は、この保存後に編集できません。"
                : "関係・年齢・経歴は初回確定済みのため変更しません。";

        new AlertDialog.Builder(this)
                .setTitle("解析結果を確認")
                .setMessage(note)
                .setView(form)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", (dialog, which) -> saveConfirmedProfile(
                        npcSelection,
                        profile,
                        name.getText().toString(),
                        relationship.getText().toString(),
                        age.getText().toString(),
                        background.getText().toString(),
                        metadataEditable))
                .show();
    }

    private void saveConfirmedProfile(
            String npcSelection,
            ImportedPersonalityProfile profile,
            String name,
            String relationship,
            String age,
            String background,
            boolean initializeMetadata
    ) {
        String npcId = NEW_NPC.equals(npcSelection) ? npcRegistry.createNpcId() : npcSelection;
        CharacterStateStore store = characterStore(npcId);
        store.saveProfile(
                safe(name, profile.name),
                profile.extraversion,
                profile.neuroticism,
                profile.agreeableness,
                profile.conscientiousness,
                profile.openness,
                profile.speechStyle
        );
        if (initializeMetadata) {
            store.initializeIdentityMetadata(relationship, age, background);
        }
        showSuccessDialog(npcId, store, profile);
    }

    private EditText input(String hint, String value, boolean enabled) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setEnabled(enabled);
        edit.setMinHeight(dp(50));
        return edit;
    }

    private void showSuccessDialog(
            String npcId,
            CharacterStateStore store,
            ImportedPersonalityProfile profile
    ) {
        new AlertDialog.Builder(this)
                .setTitle("人格を設定しました")
                .setMessage(store.displayName() + "（" + npcId + "）を保存しました。\n\n"
                        + "関係: " + store.relationshipToUser()
                        + "\n年齢: " + store.age()
                        + "\n経歴: " + store.background()
                        + "\n\n外向性 " + profile.extraversion
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
        return new CharacterStateStore(NpcContexts.storage(getApplicationContext(), npcId));
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

    private LinearLayout.LayoutParams topMargin(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(valueDp);
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
