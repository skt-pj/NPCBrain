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
    private SecureCloudflareTokenStore cloudflareTokenStore;
    private ModelSettingsStore modelSettingsStore;
    private ClefSettingsStore clefSettingsStore;
    private NpcRegistryStore registryStore;
    private NpcAiStaminaStore staminaStore;
    private TextView apiKeyStatus;
    private TextView clefAccountStatus;
    private TextView clefTokenStatus;
    private Button clefToggleButton;
    private LinearLayout budgetContainer;
    private Button cacheProbeButton;
    private TextView cacheProbeStatus;
    private volatile boolean cacheProbeRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        cloudflareTokenStore = new SecureCloudflareTokenStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        clefSettingsStore = new ClefSettingsStore(this);
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

        LinearLayout.LayoutParams clefParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clefParams.topMargin = dp(12);
        body.addView(buildClefSettingsCard(), clefParams);

        if (isDebuggableBuild()) {
            LinearLayout.LayoutParams cacheParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cacheParams.topMargin = dp(12);
            body.addView(buildPromptCacheDebugCard(), cacheParams);
        }

        TextView budgetTitle = text("NPC別 OpenAI Luna費用", 18, AppUiTheme.APP_TEXT, true);
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
        card.addView(text("OpenAI Luna設定", 18, AppUiTheme.APP_TEXT, true));

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

    private View buildClefSettingsCard() {
        LinearLayout card = card();
        card.addView(text("CLEF 行動選択", 18, AppUiTheme.APP_TEXT, true));

        TextView note = text(
                "NPCの行動選択にCloudflare CLEFを使用します。"
                        + "会話生成などの通常推論モデルは変更しません。",
                11,
                AppUiTheme.APP_MUTED,
                false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(5);
        card.addView(note, noteParams);

        TextView modelTitle = text("Decision model", 13, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams modelTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modelTitleParams.topMargin = dp(12);
        card.addView(modelTitle, modelTitleParams);

        RadioGroup models = new RadioGroup(this);
        String currentModel = clefSettingsStore.model();
        for (String model : ClefSettingsStore.supportedModels()) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(model);
            option.setText(ClefSettingsStore.displayLabel(model));
            option.setTextColor(AppUiTheme.APP_TEXT);
            option.setTextSize(12);
            option.setChecked(model.equals(currentModel));
            models.addView(option);
        }
        models.setOnCheckedChangeListener((group, checkedId) -> {
            View selected = group.findViewById(checkedId);
            if (selected != null && selected.getTag() != null) {
                clefSettingsStore.setModel(selected.getTag().toString());
                refreshClef();
            }
        });
        card.addView(models);

        clefAccountStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams accountStatusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        accountStatusParams.topMargin = dp(10);
        card.addView(clefAccountStatus, accountStatusParams);

        LinearLayout accountActions = new LinearLayout(this);
        accountActions.setOrientation(LinearLayout.HORIZONTAL);
        Button accountSave = actionButton("Account IDを設定 / 変更");
        accountSave.setOnClickListener(v -> showCloudflareAccountDialog());
        accountActions.addView(accountSave, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button accountClear = actionButton("削除");
        accountClear.setOnClickListener(v -> {
            clefSettingsStore.clearAccountId();
            refreshClef();
        });
        LinearLayout.LayoutParams accountClearParams =
                new LinearLayout.LayoutParams(dp(82), dp(48));
        accountClearParams.leftMargin = dp(7);
        accountActions.addView(accountClear, accountClearParams);
        LinearLayout.LayoutParams accountActionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        accountActionsParams.topMargin = dp(7);
        card.addView(accountActions, accountActionsParams);

        clefTokenStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams tokenStatusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tokenStatusParams.topMargin = dp(10);
        card.addView(clefTokenStatus, tokenStatusParams);

        LinearLayout tokenActions = new LinearLayout(this);
        tokenActions.setOrientation(LinearLayout.HORIZONTAL);
        Button tokenSave = actionButton("API tokenを設定 / 変更");
        tokenSave.setOnClickListener(v -> showCloudflareTokenDialog());
        tokenActions.addView(tokenSave, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button tokenClear = actionButton("削除");
        tokenClear.setOnClickListener(v -> {
            cloudflareTokenStore.clear();
            clefSettingsStore.setEnabled(false);
            refreshClef();
        });
        LinearLayout.LayoutParams tokenClearParams =
                new LinearLayout.LayoutParams(dp(82), dp(48));
        tokenClearParams.leftMargin = dp(7);
        tokenActions.addView(tokenClear, tokenClearParams);
        LinearLayout.LayoutParams tokenActionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tokenActionsParams.topMargin = dp(7);
        card.addView(tokenActions, tokenActionsParams);

        clefToggleButton = actionButton("");
        clefToggleButton.setOnClickListener(v -> toggleClef());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        toggleParams.topMargin = dp(10);
        card.addView(clefToggleButton, toggleParams);
        return card;
    }

    private void showCloudflareAccountDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Cloudflare Account ID");
        input.setText(clefSettingsStore.accountId());
        input.setSelectAllOnFocus(true);
        int pad = dp(20);
        input.setPadding(pad, dp(6), pad, dp(6));
        new AlertDialog.Builder(this)
                .setTitle("Cloudflare Account ID")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    try {
                        clefSettingsStore.setAccountId(value);
                        refreshClef();
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showCloudflareTokenDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Cloudflare API token");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = dp(20);
        input.setPadding(pad, dp(6), pad, dp(6));
        new AlertDialog.Builder(this)
                .setTitle("Cloudflare API token")
                .setMessage("tokenはAndroid Keystoreで暗号化して保存し、保存後は再表示しません。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    try {
                        cloudflareTokenStore.save(value);
                        refreshClef();
                    } catch (Exception error) {
                        Toast.makeText(this, "Cloudflare API token保存失敗", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void toggleClef() {
        if (clefSettingsStore.enabled()) {
            clefSettingsStore.setEnabled(false);
            refreshClef();
            return;
        }
        if (clefSettingsStore.accountId().isEmpty()) {
            Toast.makeText(this, "Cloudflare Account IDを設定してください。", Toast.LENGTH_LONG).show();
            return;
        }
        if (!hasCloudflareToken()) {
            Toast.makeText(this, "Cloudflare API tokenを設定してください。", Toast.LENGTH_LONG).show();
            return;
        }
        clefSettingsStore.setEnabled(true);
        refreshClef();
    }

    private boolean hasCloudflareToken() {
        try {
            return !cloudflareTokenStore.load().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void refreshClef() {
        if (clefAccountStatus != null) {
            clefAccountStatus.setText(clefSettingsStore.accountId().isEmpty()
                    ? "Cloudflare Account ID  未設定"
                    : "Cloudflare Account ID  設定済み");
        }
        if (clefTokenStatus != null) {
            clefTokenStatus.setText(hasCloudflareToken()
                    ? "Cloudflare API token  設定済み（値は非表示）"
                    : "Cloudflare API token  未設定");
        }
        if (clefToggleButton != null) {
            clefToggleButton.setText(clefSettingsStore.enabled()
                    ? "CLEF action_selection を無効化"
                    : "CLEF action_selection を有効化");
        }
    }

    private View buildPromptCacheDebugCard() {
        LinearLayout card = card();
        card.addView(text("Brain Prompt Cache Test", 18, AppUiTheme.APP_TEXT, true));

        TextView note = text(
                "Debug専用。本番Brainと同じ9専門役割・Prompt Cache構造を使います。1専門をwarm-up後、残り8専門を並列実API実行し、cached tokensを測定します。実API費用は発生しますがNPC別AI費用台帳には加算しません。",
                11,
                AppUiTheme.APP_MUTED,
                false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(5);
        card.addView(note, noteParams);

        cacheProbeButton = actionButton("脳9専門 Cacheテストを実行");
        cacheProbeButton.setOnClickListener(v -> startPromptCacheProbe());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        buttonParams.topMargin = dp(9);
        card.addView(cacheProbeButton, buttonParams);

        cacheProbeStatus = text(
                "未実行。perceptionをwarm-upし、残り8専門のparallel reuseを集計します。",
                11,
                AppUiTheme.APP_TEXT,
                false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        card.addView(cacheProbeStatus, statusParams);
        return card;
    }

    private void startPromptCacheProbe() {
        if (!isDebuggableBuild() || cacheProbeRunning) return;
        final String apiKey;
        try {
            apiKey = apiKeyStore.load().trim();
        } catch (Exception error) {
            showCacheProbeError("APIキーを読み込めませんでした。");
            return;
        }
        if (apiKey.isEmpty()) {
            showCacheProbeError("OpenAI APIキーを設定してから実行してください。");
            return;
        }

        cacheProbeRunning = true;
        if (cacheProbeButton != null) cacheProbeButton.setEnabled(false);
        if (cacheProbeStatus != null) {
            cacheProbeStatus.setText("実行中… warm-up 1件 → 8専門parallel実APIを送信しています。");
        }

        new Thread(() -> {
            try {
                PromptCacheDebugProbe.Result result = new PromptCacheDebugProbe(apiKey).run();
                runOnUiThread(() -> finishCacheProbe(result.displayText()));
            } catch (Exception error) {
                String detail = error.getMessage();
                if (detail == null || detail.trim().isEmpty()) {
                    detail = error.getClass().getSimpleName();
                }
                final String message = detail;
                runOnUiThread(() -> showCacheProbeError(message));
            }
        }, "prompt-cache-debug-probe").start();
    }

    private void finishCacheProbe(String result) {
        cacheProbeRunning = false;
        if (isFinishing() || isDestroyed()) return;
        if (cacheProbeButton != null) cacheProbeButton.setEnabled(true);
        if (cacheProbeStatus != null) cacheProbeStatus.setText(result == null ? "" : result);
    }

    private void showCacheProbeError(String message) {
        cacheProbeRunning = false;
        if (isFinishing() || isDestroyed()) return;
        if (cacheProbeButton != null) cacheProbeButton.setEnabled(true);
        if (cacheProbeStatus != null) {
            cacheProbeStatus.setText("ERROR: " + (message == null ? "不明なエラー" : message));
        }
    }

    private void refresh() {
        refreshClef();
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
        int shown = 0;
        for (String npcId : ids) {
            if (!NpcInferenceAccess.usesOpenAi(this, npcId)) continue;
            budgetContainer.addView(buildBudgetCard(npcId));
            shown++;
        }
        if (shown == 0) {
            budgetContainer.addView(text(
                    "OpenAI Lunaを選択中のNPCはいません。",
                    12, AppUiTheme.APP_MUTED, false));
        }
    }

    private View buildBudgetCard(String npcId) {
        NpcAiStaminaStore.Snapshot snapshot = staminaStore.snapshot(npcId);
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
        String name = character.displayName();
        if (name == null || name.trim().isEmpty() || "NPC".equals(name.trim())) {
            name = npcId.toUpperCase(Locale.US);
        }
        if (character.isDead()) name += "（死亡）";
        final String displayName = name;

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

    private boolean isDebuggableBuild() {
        return (getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
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
