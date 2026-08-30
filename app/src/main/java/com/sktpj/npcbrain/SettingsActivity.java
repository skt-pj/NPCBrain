package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Global app settings and canonical per-NPC AI budget controls. */
public final class SettingsActivity extends Activity {
    private SecureApiKeyStore apiKeyStore;
    private ModelSettingsStore modelSettingsStore;
    private NpcRegistryStore registryStore;
    private NpcAiStaminaStore staminaStore;
    private TextView apiKeyStatus;
    private LinearLayout budgetContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        registryStore = new NpcRegistryStore(this);
        staminaStore = new NpcAiStaminaStore(this);
        setContentView(buildContent());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUiTheme.APP_BACKGROUND);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("NPCBRAIN", 10, AppUiTheme.APP_MUTED, true);
        eyebrow.setLetterSpacing(0.16f);
        header.addView(eyebrow);
        header.addView(text("設定", 26, AppUiTheme.APP_TEXT, true));
        TextView note = text(
                "アプリ全体のAI設定と、NPCごとの費用上限・使用量を管理します。",
                11,
                AppUiTheme.APP_MUTED,
                false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(3);
        header.addView(note, noteParams);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(12), 0, dp(18));
        scroll.addView(body);

        body.addView(buildAiSettingsCard());

        TextView budgetTitle = text("NPC別 AI費用", 18, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams budgetTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        budgetTitleParams.topMargin = dp(18);
        body.addView(budgetTitle, budgetTitleParams);

        TextView budgetNote = text(
                "上限は各NPCの会話・自発会話・記憶処理・ダンジョン認知など、NPCに紐づくOpenAI利用全体へ適用されます。リセットしても累計は残ります。",
                11,
                AppUiTheme.APP_MUTED,
                false);
        body.addView(budgetNote);

        budgetContainer = new LinearLayout(this);
        budgetContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = dp(8);
        body.addView(budgetContainer, containerParams);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildAiSettingsCard() {
        LinearLayout card = card();
        card.addView(text("AI設定", 18, AppUiTheme.APP_TEXT, true));

        TextView model = text("モデル  gpt-5.6-luna", 12, AppUiTheme.APP_MUTED, false);
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modelParams.topMargin = dp(5);
        card.addView(model, modelParams);

        apiKeyStatus = text("", 13, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams keyStatusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        keyStatusParams.topMargin = dp(10);
        card.addView(apiKeyStatus, keyStatusParams);

        LinearLayout keyActions = new LinearLayout(this);
        keyActions.setOrientation(LinearLayout.HORIZONTAL);
        Button saveKey = actionButton("APIキーを設定 / 変更");
        saveKey.setOnClickListener(v -> showApiKeyDialog());
        keyActions.addView(saveKey, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button clearKey = actionButton("削除");
        clearKey.setOnClickListener(v -> confirmClearApiKey());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(82), dp(48));
        clearParams.leftMargin = dp(7);
        keyActions.addView(clearKey, clearParams);
        LinearLayout.LayoutParams keyActionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        keyActionsParams.topMargin = dp(7);
        card.addView(keyActions, keyActionsParams);

        TextView effortTitle = text("推論モード", 13, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams effortTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        effortTitleParams.topMargin = dp(14);
        card.addView(effortTitle, effortTitleParams);

        RadioGroup efforts = new RadioGroup(this);
        String current = modelSettingsStore.reasoningEffort();
        for (String effort : ModelSettingsStore.supportedEfforts()) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(effort);
            option.setText(ModelSettingsStore.displayLabel(effort) + " — "
                    + ModelSettingsStore.description(effort));
            option.setTextColor(AppUiTheme.APP_TEXT);
            option.setTextSize(12);
            option.setChecked(effort.equals(current));
            efforts.addView(option);
        }
        efforts.setOnCheckedChangeListener((group, checkedId) -> {
            View selected = group.findViewById(checkedId);
            if (selected != null && selected.getTag() != null) {
                modelSettingsStore.setReasoningEffort(selected.getTag().toString());
            }
        });
        card.addView(efforts);
        return card;
    }

    private void refresh() {
        if (apiKeyStatus != null) {
            apiKeyStatus.setText(hasApiKey()
                    ? "OpenAI APIキー  設定済み（値は非表示）"
                    : "OpenAI APIキー  未設定");
        }
        renderBudgetCards();
    }

    private void renderBudgetCards() {
        if (budgetContainer == null) return;
        budgetContainer.removeAllViews();
        List<String> ids = registryStore.npcIds();
        if (ids.isEmpty()) {
            budgetContainer.addView(text("NPCがありません。", 12, AppUiTheme.APP_MUTED, false));
            return;
        }
        for (String npcId : ids) budgetContainer.addView(buildBudgetCard(npcId));
    }

    private View buildBudgetCard(String npcId) {
        NpcAiStaminaStore.Snapshot snapshot = staminaStore.snapshot(npcId);
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
        String displayName = character.displayName();
        if (displayName == null || displayName.trim().isEmpty() || "NPC".equals(displayName.trim())) {
            displayName = npcId.toUpperCase(Locale.US);
        }
        if (character.isDead()) displayName += "（死亡）";

        LinearLayout card = card();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);

        card.addView(text(displayName + "  ·  " + npcId, 15, AppUiTheme.APP_TEXT, true));

        TextView current = text(
                "現在枠  " + NpcAiUsageDisplayPolicy.formatSpentJpy(snapshot.spentJpy)
                        + " / " + NpcAiUsageDisplayPolicy.formatRemainingJpy(snapshot.budgetLimitJpy)
                        + "  ·  残 " + NpcAiUsageDisplayPolicy.formatRemainingJpy(snapshot.remainingJpy)
                        + "  (" + snapshot.remainingPercent + "%)",
                12,
                AppUiTheme.APP_TEXT,
                false);
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        currentParams.topMargin = dp(5);
        card.addView(current, currentParams);

        TextView lifetime = text(
                "累計  " + NpcAiUsageDisplayPolicy.formatSpentJpy(snapshot.lifetimeSpentJpy)
                        + "  ·  total token "
                        + String.format(Locale.JAPAN, "%,d", snapshot.lifetimeTotalTokens)
                        + "\ninput " + String.format(Locale.JAPAN, "%,d", snapshot.lifetimeInputTokens)
                        + "  ·  cached " + String.format(Locale.JAPAN, "%,d", snapshot.lifetimeCachedInputTokens)
                        + "  ·  output " + String.format(Locale.JAPAN, "%,d", snapshot.lifetimeOutputTokens),
                11,
                AppUiTheme.APP_MUTED,
                false);
        card.addView(lifetime);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        EditText limit = new EditText(this);
        limit.setSingleLine(true);
        limit.setText(String.format(Locale.US, "%.2f", snapshot.budgetLimitJpy));
        limit.setSelectAllOnFocus(true);
        limit.setHint("上限 JPY");
        limit.setTextColor(AppUiTheme.APP_TEXT);
        limit.setHintTextColor(AppUiTheme.APP_MUTED);
        limit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        controls.addView(limit, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button save = actionButton("上限保存");
        save.setOnClickListener(v -> saveBudgetLimit(npcId, limit));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(92), dp(48));
        saveParams.leftMargin = dp(6);
        controls.addView(save, saveParams);

        Button reset = actionButton("枠リセット");
        reset.setOnClickListener(v -> confirmResetBudget(npcId, displayName));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(96), dp(48));
        resetParams.leftMargin = dp(6);
        controls.addView(reset, resetParams);

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        controlsParams.topMargin = dp(8);
        card.addView(controls, controlsParams);
        return card;
    }

    private void saveBudgetLimit(String npcId, EditText input) {
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        final double value;
        try {
            value = Double.parseDouble(raw);
        } catch (Exception error) {
            Toast.makeText(this, "上限は数値で入力してください。", Toast.LENGTH_LONG).show();
            return;
        }
        if (value < NpcAiBudgetPolicy.MIN_BUDGET_JPY
                || value > NpcAiBudgetPolicy.MAX_BUDGET_JPY) {
            Toast.makeText(
                    this,
                    "上限は ¥0.01〜¥100,000 の範囲で設定してください。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        staminaStore.setBudgetLimitJpy(npcId, value);
        Toast.makeText(this, "費用上限を保存しました。", Toast.LENGTH_SHORT).show();
        renderBudgetCards();
    }

    private void confirmResetBudget(String npcId, String displayName) {
        new AlertDialog.Builder(this)
                .setTitle("現在の費用枠をリセット")
                .setMessage(displayName + " の現在枠の消費額とtokenを0にします。\n\n累計費用・累計tokenと費用上限は消えません。")
                .setPositiveButton("リセット", (dialog, which) -> {
                    staminaStore.resetCurrentBudget(npcId);
                    renderBudgetCards();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("sk-...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = dp(20);
        input.setPadding(pad, dp(6), pad, dp(6));
        new AlertDialog.Builder(this)
                .setTitle("OpenAI APIキー")
                .setMessage("APIキーはAndroid Keystoreで暗号化して保存します。保存済みの値は画面へ再表示しません。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    try {
                        apiKeyStore.save(value);
                        refresh();
                    } catch (Exception error) {
                        Toast.makeText(this, "APIキー保存失敗", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void confirmClearApiKey() {
        new AlertDialog.Builder(this)
                .setTitle("APIキーを削除")
                .setMessage("保存済みのOpenAI APIキーを削除します。")
                .setPositiveButton("削除", (dialog, which) -> {
                    apiKeyStore.clear();
                    refresh();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private boolean hasApiKey() {
        try {
            return !apiKeyStore.load().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppUiTheme.APP_SURFACE);
        bg.setStroke(dp(1), AppUiTheme.APP_BORDER);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        return card;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(AppUiTheme.APP_TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(27, 47, 69));
        bg.setStroke(dp(1), Color.rgb(55, 82, 111));
        bg.setCornerRadius(dp(10));
        button.setBackground(bg);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
