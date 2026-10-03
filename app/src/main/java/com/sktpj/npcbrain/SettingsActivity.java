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
import android.widget.CheckBox;
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
    private RoutingSettingsStore routingSettingsStore;
    private LocalInferenceSettingsStore localInferenceSettingsStore;
    private SpecialistInferenceSettingsStore specialistInferenceSettingsStore;
    private DecisionModelSettingsStore decisionModelSettingsStore;
    private SecureCloudflareTokenStore cloudflareTokenStore;
    private ClefPerformanceStore clefPerformanceStore;
    private NpcRegistryStore registryStore;
    private NpcAiStaminaStore staminaStore;
    private TextView apiKeyStatus;
    private TextView clefModelStatus;
    private Button clefModelButton;
    private TextView clefPerformanceStatus;
    private LinearLayout localModelsContainer;
    private LinearLayout budgetContainer;
    private Button cacheProbeButton;
    private TextView cacheProbeStatus;
    private volatile boolean cacheProbeRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        routingSettingsStore = new RoutingSettingsStore(this);
        localInferenceSettingsStore = new LocalInferenceSettingsStore(this);
        specialistInferenceSettingsStore = new SpecialistInferenceSettingsStore(this);
        decisionModelSettingsStore = new DecisionModelSettingsStore(this);
        cloudflareTokenStore = new SecureCloudflareTokenStore(this);
        clefPerformanceStore = new ClefPerformanceStore(this);
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
        header.addView(text("AI管理", 26, AppUiTheme.APP_TEXT, true));
        TextView note = text(
                "Global Workspaceは通常LLM、分割脳は「通常LLM / 判断モデル」のどちらか一方を設定します。NPC個別のLLM上書きはDEBUGのNPC管理で行います。",
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

        body.addView(buildCurrentConfigurationCard());

        LinearLayout.LayoutParams routingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        routingParams.topMargin = dp(12);
        body.addView(buildRoutingCard(), routingParams);

        LinearLayout.LayoutParams openAiParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        openAiParams.topMargin = dp(12);
        body.addView(buildAiSettingsCard(), openAiParams);

        LinearLayout.LayoutParams localParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        localParams.topMargin = dp(12);
        body.addView(buildLocalModelsCard(), localParams);

        LinearLayout.LayoutParams clefParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clefParams.topMargin = dp(12);
        body.addView(buildClefSettingsCard(), clefParams);

        LinearLayout.LayoutParams clefPerformanceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clefPerformanceParams.topMargin = dp(12);
        body.addView(buildClefPerformanceCard(), clefPerformanceParams);

        if (isDebuggableBuild()) {
            LinearLayout.LayoutParams cacheParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cacheParams.topMargin = dp(12);
            body.addView(buildPromptCacheDebugCard(), cacheParams);
        }

        TextView budgetTitle = text("通常LLM · NPC別 OpenAI Luna費用", 18, AppUiTheme.APP_TEXT, true);
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

    private View buildCurrentConfigurationCard() {
        LinearLayout card = card();
        card.addView(text("現在の構成", 18, AppUiTheme.APP_TEXT, true));
        addConfigurationRow(
                card,
                "Global Workspace",
                "通常LLM / " + routeSummary(routingSettingsStore.globalModel()));
        addConfigurationRow(
                card,
                "分割脳（9専門）",
                specialistSummary());
        return card;
    }

    private void addConfigurationRow(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(9);
        card.addView(row, rowParams);

        row.addView(text(label, 12, AppUiTheme.APP_TEXT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.40f));
        TextView detail = text(value, 11, AppUiTheme.APP_MUTED, false);
        detail.setGravity(Gravity.END);
        row.addView(detail,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.60f));
    }

    private String routeSummary(String model) {
        return NpcInferenceModel.executionLocationLabel(model)
                + " / " + NpcInferenceModel.displayLabel(model);
    }

    private String specialistSummary() {
        if (!specialistInferenceSettingsStore.usesDecisionModel()) {
            return "通常LLM / " + routeSummary(routingSettingsStore.specialistModel());
        }
        String model = decisionModelSettingsStore.model();
        return "判断モデル / "
                + DecisionModelCatalog.executionLocationLabel(model)
                + " / " + DecisionModelCatalog.displayLabel(model);
    }

    private View buildRoutingCard() {
        LinearLayout card = card();
        card.addView(text("脳への割り当て", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "Global Workspaceは通常LLMで統合します。分割脳の9専門は「通常LLM」か「判断モデル」のどちらか一方だけで動きます。"
                        + " 9専門の一部だけを別モデルにする設定はありません。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        addGlobalRoutingGroup(card);
        addSpecialistRoutingGroup(card);
        return card;
    }

    private void addGlobalRoutingGroup(LinearLayout card) {
        TextView heading = text("Global Workspace", 15, AppUiTheme.APP_TEXT, true);
        card.addView(heading, matchTop(dp(16)));
        card.addView(text(
                "最終統合・発話・行動決定を行う通常LLMです。",
                10,
                AppUiTheme.APP_MUTED,
                false));
        addLlmRouteControls(card, true, 1);
    }

    private void addSpecialistRoutingGroup(LinearLayout card) {
        TextView heading = text("分割脳（9専門）", 15, AppUiTheme.APP_TEXT, true);
        card.addView(heading, matchTop(dp(18)));
        card.addView(text(
                "知覚・注意・記憶・世界モデル・実行制御・価値判断・誤り監視・行動選択など9専門を同じ推論方式で並列実行します。",
                10,
                AppUiTheme.APP_MUTED,
                false));

        TextView typeTitle = text("① 推論方式", 12, AppUiTheme.APP_TEXT, true);
        card.addView(typeTitle, matchTop(dp(9)));

        RadioGroup type = new RadioGroup(this);
        type.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton llm = routeRadio("通常LLM");
        RadioButton decision = routeRadio("判断モデル");
        int llmId = View.generateViewId();
        int decisionId = View.generateViewId();
        llm.setId(llmId);
        decision.setId(decisionId);
        type.addView(llm, new RadioGroup.LayoutParams(0, dp(44), 1f));
        type.addView(decision, new RadioGroup.LayoutParams(0, dp(44), 1f));
        type.check(specialistInferenceSettingsStore.usesDecisionModel()
                ? decisionId
                : llmId);
        type.setOnCheckedChangeListener((group, checkedId) -> {
            boolean nextDecision = checkedId == decisionId;
            if (nextDecision == specialistInferenceSettingsStore.usesDecisionModel()) return;
            specialistInferenceSettingsStore.setMode(nextDecision
                    ? SpecialistInferenceSettingsStore.MODE_DECISION_MODEL
                    : SpecialistInferenceSettingsStore.MODE_LLM);
            rebuildContent();
        });
        card.addView(type);

        if (specialistInferenceSettingsStore.usesDecisionModel()) {
            addDecisionModelControls(card);
        } else {
            addLlmRouteControls(card, false, 2);
        }
    }

    private void addLlmRouteControls(
            LinearLayout card,
            boolean global,
            int firstStep
    ) {
        String current = global
                ? routingSettingsStore.globalModel()
                : routingSettingsStore.specialistModel();

        TextView locationTitle = text(
                "①②③④".substring(firstStep - 1, firstStep) + " 実行場所",
                12,
                AppUiTheme.APP_TEXT,
                true);
        card.addView(locationTitle, matchTop(dp(8)));

        RadioGroup location = new RadioGroup(this);
        location.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton local = routeRadio("ローカル");
        RadioButton cloud = routeRadio("クラウド");
        int localId = View.generateViewId();
        int cloudId = View.generateViewId();
        local.setId(localId);
        cloud.setId(cloudId);
        location.addView(local, new RadioGroup.LayoutParams(0, dp(44), 1f));
        location.addView(cloud, new RadioGroup.LayoutParams(0, dp(44), 1f));
        location.check(NpcInferenceModel.isLocal(current) ? localId : cloudId);
        location.setOnCheckedChangeListener((group, checkedId) -> {
            boolean cloudSelected = checkedId == cloudId;
            boolean alreadyCloud = !NpcInferenceModel.isLocal(
                    global ? routingSettingsStore.globalModel()
                            : routingSettingsStore.specialistModel());
            if (cloudSelected == alreadyCloud) return;
            if (global) routingSettingsStore.setGlobalCloud(cloudSelected);
            else routingSettingsStore.setSpecialistCloud(cloudSelected);
            rebuildContent();
        });
        card.addView(location);

        TextView modelTitle = text(
                "①②③④".substring(firstStep, firstStep + 1) + " モデル",
                12,
                AppUiTheme.APP_TEXT,
                true);
        card.addView(modelTitle, matchTop(dp(8)));

        Button modelButton = actionButton(NpcInferenceModel.displayLabel(current) + "  ▼");
        modelButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        modelButton.setPadding(dp(12), 0, dp(12), 0);
        modelButton.setOnClickListener(v -> showModelPicker(global));
        card.addView(modelButton, matchTop(dp(5)));

        TextView detailTitle = text(
                "①②③④".substring(firstStep + 1, firstStep + 2) + " 詳細",
                12,
                AppUiTheme.APP_TEXT,
                true);
        card.addView(detailTitle, matchTop(dp(9)));

        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView detailSummary = text(
                NpcInferenceModel.isLocal(current)
                        ? localDetailSummary(current)
                        : cloudDetailSummary(),
                10,
                AppUiTheme.APP_MUTED,
                false);
        detailRow.addView(detailSummary,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button details = actionButton("詳細を選択");
        details.setOnClickListener(v -> {
            String label = global ? "Global Workspace" : "分割脳（通常LLM）";
            if (NpcInferenceModel.isLocal(current)) {
                showLocalDetailDialog(label, current);
            } else {
                showOpenAiDetailDialog(label + " 詳細設定");
            }
        });
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(dp(112), dp(42));
        detailsParams.leftMargin = dp(7);
        detailRow.addView(details, detailsParams);
        card.addView(detailRow, matchTop(dp(5)));
    }

    private void addDecisionModelControls(LinearLayout card) {
        String current = decisionModelSettingsStore.model();

        card.addView(text("② 実行場所", 12, AppUiTheme.APP_TEXT, true), matchTop(dp(8)));
        RadioGroup location = new RadioGroup(this);
        location.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton local = routeRadio("ローカル");
        RadioButton cloud = routeRadio("クラウド");
        int localId = View.generateViewId();
        int cloudId = View.generateViewId();
        local.setId(localId);
        cloud.setId(cloudId);
        location.addView(local, new RadioGroup.LayoutParams(0, dp(44), 1f));
        location.addView(cloud, new RadioGroup.LayoutParams(0, dp(44), 1f));
        location.check(DecisionModelCatalog.isLocal(current) ? localId : cloudId);
        location.setOnCheckedChangeListener((group, checkedId) -> {
            boolean nextCloud = checkedId == cloudId;
            if (nextCloud == DecisionModelCatalog.isCloud(decisionModelSettingsStore.model())) {
                return;
            }
            decisionModelSettingsStore.setCloud(nextCloud);
            rebuildContent();
        });
        card.addView(location);

        card.addView(text("③ 判断モデル", 12, AppUiTheme.APP_TEXT, true), matchTop(dp(8)));
        Button model = actionButton(DecisionModelCatalog.displayLabel(current) + "  ▼");
        model.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        model.setPadding(dp(12), 0, dp(12), 0);
        model.setOnClickListener(v -> showDecisionModelPicker());
        card.addView(model, matchTop(dp(5)));

        card.addView(text("④ 詳細", 12, AppUiTheme.APP_TEXT, true), matchTop(dp(9)));
        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView summary = text(
                decisionModelDetailSummary(current),
                10,
                AppUiTheme.APP_MUTED,
                false);
        detailRow.addView(summary,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button details = actionButton("詳細を選択");
        details.setOnClickListener(v -> showDecisionModelDetailDialog());
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(dp(112), dp(42));
        detailsParams.leftMargin = dp(7);
        detailRow.addView(details, detailsParams);
        card.addView(detailRow, matchTop(dp(5)));
    }

    private RadioButton routeRadio(String label) {
        RadioButton option = new RadioButton(this);
        option.setText(label);
        option.setTextColor(AppUiTheme.APP_TEXT);
        option.setTextSize(12);
        option.setGravity(Gravity.CENTER_VERTICAL);
        return option;
    }

    private void showModelPicker(boolean global) {
        String current = global
                ? routingSettingsStore.globalModel()
                : routingSettingsStore.specialistModel();
        boolean local = NpcInferenceModel.isLocal(current);
        String[] values = local ? NpcInferenceModel.localValues() : NpcInferenceModel.cloudValues();
        String[] labels = new String[values.length];
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = NpcInferenceModel.displayLabel(values[i])
                    + (local ? "  ·  " + NpcInferenceModel.loadLabel(values[i]) : "");
            if (values[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(local ? "ローカルLLMを選択" : "クラウドLLMを選択")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which < 0 || which >= values.length) return;
                    if (global) routingSettingsStore.setGlobalModel(values[which]);
                    else routingSettingsStore.setSpecialistModel(values[which]);
                    dialog.dismiss();
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showDecisionModelPicker() {
        String current = decisionModelSettingsStore.model();
        boolean local = DecisionModelCatalog.isLocal(current);
        String[] values = local
                ? DecisionModelCatalog.localValues()
                : DecisionModelCatalog.cloudValues();
        String[] labels = new String[values.length];
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = DecisionModelCatalog.displayLabel(values[i]);
            if (values[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(local ? "ローカル判断モデルを選択" : "クラウド判断モデルを選択")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which < 0 || which >= values.length) return;
                    decisionModelSettingsStore.setModel(values[which]);
                    dialog.dismiss();
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private String localDetailSummary(String modelId) {
        LocalModelDownloadManager.Snapshot snapshot =
                LocalModelDownloadManager.snapshot(this, modelId);
        String state = snapshot.downloaded
                ? "DL済み"
                : snapshot.downloading ? "DL中" : "未DL";
        return "重み: " + localWeightLabel(modelId)
                + "\n" + localInferenceSettingsStore.summary()
                + " · " + state;
    }

    private String cloudDetailSummary() {
        return "Reasoning: "
                + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort())
                + " · APIキー " + (hasApiKey() ? "設定済み" : "未設定");
    }

    private String decisionModelDetailSummary(String modelId) {
        if (DecisionModelCatalog.isLocal(modelId)) {
            ClefLocalDownloadManager.Snapshot snapshot =
                    ClefLocalDownloadManager.snapshot(this);
            return "System One / typed decision"
                    + "\nQ4_K_M · " + snapshot.displayText();
        }
        return "System One / typed decision"
                + "\nCloudflare認証 "
                + (decisionModelSettingsStore.cloudflareAccountId().isEmpty()
                ? "未設定" : "Account設定済み")
                + " · token " + (hasCloudflareToken() ? "設定済み" : "未設定");
    }

    private String localWeightLabel(String modelId) {
        String normalized = NpcInferenceModel.normalize(modelId);
        if (NpcInferenceModel.LOCAL_MEDIUM.equals(normalized)) {
            return "Q8 / EKV4096（配布artifact固定）";
        }
        return "LiteRT-LM配布artifact固定";
    }

    private void showLocalDetailDialog(String routeTitle, String modelId) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(4));
        scroll.addView(content);

        content.addView(text(
                NpcInferenceModel.displayLabel(modelId)
                        + "（ローカル実行）の共通詳細設定",
                13,
                AppUiTheme.APP_TEXT,
                true));
        content.addView(text(
                "この設定は通常LLMのローカルruntimeで共通利用します。",
                10,
                AppUiTheme.APP_MUTED,
                false),
                matchTop(dp(4)));

        LocalModelRepository.ModelSpec spec = LocalModelRepository.spec(modelId);
        LocalModelDownloadManager.Snapshot snapshot =
                LocalModelDownloadManager.snapshot(this, modelId);
        content.addView(text(
                "重み / 量子化  " + localWeightLabel(modelId)
                        + "\n負荷目安  " + NpcInferenceModel.loadLabel(modelId)
                        + "\nモデルファイル  " + spec.fileName
                        + "\n状態  " + snapshot.displayText(),
                11,
                AppUiTheme.APP_MUTED,
                false),
                matchTop(dp(10)));

        CheckBox preferGpu = detailCheckBox(
                "GPUを優先する",
                "GPUで初期化し、失敗時だけCPUへ切り替えます。",
                localInferenceSettingsStore.preferGpu());
        content.addView(preferGpu, matchTop(dp(12)));

        CheckBox autoCompact = detailCheckBox(
                "長い入力を自動圧縮する",
                "モデルのcontext上限を超える場合、grounded情報を段階的に圧縮します。",
                localInferenceSettingsStore.autoCompact());
        content.addView(autoCompact, matchTop(dp(6)));

        CheckBox retryJson = detailCheckBox(
                "JSON失敗時に1回再試行する",
                "不完全JSONだった場合だけ、短いJSONを1回再生成します。",
                localInferenceSettingsStore.retryInvalidJson());
        content.addView(retryJson, matchTop(dp(6)));

        new AlertDialog.Builder(this)
                .setTitle(routeTitle + " 詳細設定")
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    localInferenceSettingsStore.setPreferGpu(preferGpu.isChecked());
                    localInferenceSettingsStore.setAutoCompact(autoCompact.isChecked());
                    localInferenceSettingsStore.setRetryInvalidJson(retryJson.isChecked());
                    LocalLlmRuntime.resetEngines();
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private CheckBox detailCheckBox(
            String title,
            String description,
            boolean checked
    ) {
        CheckBox box = new CheckBox(this);
        box.setChecked(checked);
        box.setText(title + "\n" + description);
        box.setTextColor(AppUiTheme.APP_TEXT);
        box.setTextSize(11);
        box.setPadding(0, dp(3), 0, dp(3));
        return box;
    }

    private void showOpenAiDetailDialog(String title) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(4));
        content.addView(text(
                "OpenAI共通設定 · GPT-6 Luna / GPT-5.6 Luna",
                13,
                AppUiTheme.APP_TEXT,
                true));
        content.addView(text(
                "このReasoning設定はOpenAIの通常LLMで共通利用します。"
                        + "\nAPIキー  " + (hasApiKey() ? "設定済み" : "未設定"),
                10,
                AppUiTheme.APP_MUTED,
                false),
                matchTop(dp(4)));

        TextView effortTitle = text("推論モード", 12, AppUiTheme.APP_TEXT, true);
        content.addView(effortTitle, matchTop(dp(10)));

        RadioGroup efforts = new RadioGroup(this);
        String current = modelSettingsStore.reasoningEffort();
        for (String effort : ModelSettingsStore.supportedEfforts()) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(effort);
            option.setText(ModelSettingsStore.displayLabel(effort)
                    + " — " + ModelSettingsStore.description(effort));
            option.setTextColor(AppUiTheme.APP_TEXT);
            option.setTextSize(11);
            option.setChecked(effort.equals(current));
            efforts.addView(option);
        }
        content.addView(efforts);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setPositiveButton("保存", (dialog, which) -> {
                    int checkedId = efforts.getCheckedRadioButtonId();
                    View selected = efforts.findViewById(checkedId);
                    if (selected != null && selected.getTag() != null) {
                        modelSettingsStore.setReasoningEffort(selected.getTag().toString());
                    }
                    rebuildContent();
                })
                .setNeutralButton("APIキー設定", (dialog, which) -> showApiKeyDialog())
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showDecisionModelDetailDialog() {
        String model = decisionModelSettingsStore.model();
        if (DecisionModelCatalog.isCloud(model)) {
            showCloudflareCredentialsDialog();
            return;
        }
        ClefLocalDownloadManager.Snapshot snapshot =
                ClefLocalDownloadManager.snapshot(this);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("判断モデル 詳細")
                .setMessage(
                        "モデル: " + DecisionModelCatalog.displayLabel(model)
                                + "\n実行: ローカル"
                                + "\n方式: System One typed decision"
                                + "\n自由文生成: なし"
                                + "\n対象: 分割脳9専門すべて"
                                + "\n状態: " + snapshot.displayText())
                .setPositiveButton("閉じる", null);
        if (snapshot.downloading) {
            dialog.setNeutralButton("ダウンロード中", null);
        } else if (snapshot.downloaded) {
            dialog.setNeutralButton("モデル削除", (d, which) -> {
                ClefLocalDownloadManager.deleteModel(this);
                rebuildContent();
            });
        } else {
            dialog.setNeutralButton("ダウンロード", (d, which) -> {
                ClefLocalDownloadManager.startDownload(this);
                refreshClef();
            });
        }
        dialog.show();
    }

    private void showCloudflareCredentialsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(4));

        EditText account = new EditText(this);
        account.setSingleLine(true);
        account.setHint("Cloudflare Account ID");
        account.setText(decisionModelSettingsStore.cloudflareAccountId());
        account.setSelectAllOnFocus(true);
        content.addView(account);

        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setHint(hasCloudflareToken()
                ? "API token（変更する場合だけ入力）"
                : "Cloudflare API token");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(token, matchTop(dp(8)));

        new AlertDialog.Builder(this)
                .setTitle("クラウド判断モデル認証")
                .setMessage("Cloudflare Workers AIの判断モデル用です。tokenはAndroid Keystoreで暗号化し、保存後は再表示しません。")
                .setView(content)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        decisionModelSettingsStore.setCloudflareAccountId(
                                account.getText() == null ? "" : account.getText().toString());
                        String value = token.getText() == null
                                ? ""
                                : token.getText().toString().trim();
                        if (!value.isEmpty()) cloudflareTokenStore.save(value);
                        rebuildContent();
                    } catch (Exception error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("認証情報削除", (dialog, which) -> {
                    decisionModelSettingsStore.clearCloudflareAccountId();
                    cloudflareTokenStore.clear();
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private boolean hasCloudflareToken() {
        try {
            return !cloudflareTokenStore.load().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private View buildLocalModelsCard() {
        LinearLayout card = card();
        card.addView(text("通常LLM · ローカルモデル管理", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "モデル名を直接選びます。軽量 / 中量 / 高負荷はモデルの補助情報で、選択値ではありません。",
                11,
                AppUiTheme.APP_MUTED,
                false));
        localModelsContainer = new LinearLayout(this);
        localModelsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(localModelsContainer);
        refreshLocalModels();
        return card;
    }

    private void refreshLocalModels() {
        if (localModelsContainer == null) return;
        localModelsContainer.removeAllViews();
        addLocalModelStatus(NpcInferenceModel.LOCAL_LIGHT);
        addLocalModelStatus(NpcInferenceModel.LOCAL_MEDIUM);
        addLocalModelStatus(NpcInferenceModel.LOCAL_HEAVY);
        if (LocalModelDownloadManager.anyDownloading()) {
            LinearLayout target = localModelsContainer;
            target.postDelayed(() -> {
                if (target == localModelsContainer) refreshLocalModels();
            }, 750L);
        }
    }

    private void addLocalModelStatus(String modelId) {
        LocalModelRepository.ModelSpec spec = LocalModelRepository.spec(modelId);
        LocalModelDownloadManager.Snapshot snapshot =
                LocalModelDownloadManager.snapshot(this, modelId);

        TextView title = text(
                NpcInferenceModel.displayLabel(modelId)
                        + "  ·  " + NpcInferenceModel.loadLabel(modelId),
                12,
                AppUiTheme.APP_TEXT,
                true);
        localModelsContainer.addView(title, matchTop(dp(10)));
        localModelsContainer.addView(text(
                "重み  " + localWeightLabel(modelId)
                        + "\n" + spec.repository
                        + "\n" + snapshot.displayText(),
                10,
                AppUiTheme.APP_MUTED,
                false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button detail = actionButton("詳細設定");
        detail.setOnClickListener(v -> showLocalDetailDialog(
                NpcInferenceModel.displayLabel(modelId), modelId));
        actions.addView(detail, new LinearLayout.LayoutParams(0, dp(42), 1f));

        if (!snapshot.downloaded) {
            Button download = actionButton(snapshot.downloading
                    ? "ダウンロード中…"
                    : snapshot.errorMessage.isEmpty() ? "ダウンロード" : "再ダウンロード");
            download.setEnabled(!snapshot.downloading);
            download.setOnClickListener(v -> {
                LocalModelDownloadManager.startDownload(this, modelId);
                refreshLocalModels();
            });
            LinearLayout.LayoutParams downloadParams =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            downloadParams.leftMargin = dp(6);
            actions.addView(download, downloadParams);
        }
        localModelsContainer.addView(actions, matchTop(dp(6)));
    }

    private View buildAiSettingsCard() {
        LinearLayout card = card();
        card.addView(text("通常LLM · クラウド設定", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text("Provider  OpenAI", 12, AppUiTheme.APP_TEXT, true), matchTop(dp(7)));
        card.addView(text("モデル  GPT-6 Luna / GPT-5.6 Luna", 11, AppUiTheme.APP_MUTED, false));
        card.addView(text(
                "Reasoning  " + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort()),
                11,
                AppUiTheme.APP_MUTED,
                false));

        apiKeyStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        card.addView(apiKeyStatus, matchTop(dp(8)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button details = actionButton("OpenAI共通詳細");
        details.setOnClickListener(v -> showOpenAiDetailDialog("OpenAI 共通設定"));
        actions.addView(details, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button clearKey = actionButton("APIキー削除");
        clearKey.setOnClickListener(v -> confirmClearApiKey());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(104), dp(44));
        clearParams.leftMargin = dp(6);
        actions.addView(clearKey, clearParams);
        card.addView(actions, matchTop(dp(8)));
        return card;
    }

    private View buildClefSettingsCard() {
        LinearLayout card = card();
        card.addView(text("判断モデル管理", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "分割脳で「判断モデル」を選んだ場合だけ使用します。通常LLMと同時には実行しません。"
                        + " 判断モデルは自由文を生成せず、stateとtyped questionから選択肢の確率を返します。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        card.addView(text("ローカル", 13, AppUiTheme.APP_TEXT, true), matchTop(dp(11)));
        card.addView(text(
                "Clef-flash 9B · Q4_K_M · 約6.49 GB"
                        + "\nggml-org/Clef-Flash-GGUF · architecture=clef / joint head込み",
                11,
                AppUiTheme.APP_MUTED,
                false));

        clefModelStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        card.addView(clefModelStatus, matchTop(dp(7)));

        clefModelButton = actionButton("");
        clefModelButton.setOnClickListener(v -> handleClefModelButton());
        card.addView(clefModelButton, matchTop(dp(7)));

        card.addView(text("クラウド", 13, AppUiTheme.APP_TEXT, true), matchTop(dp(14)));
        card.addView(text(
                "Provider  Cloudflare Workers AI"
                        + "\nモデル  Clef-flash 9B / Clef 27B",
                11,
                AppUiTheme.APP_MUTED,
                false));
        card.addView(text(
                "Account ID  "
                        + (decisionModelSettingsStore.cloudflareAccountId().isEmpty()
                        ? "未設定" : "設定済み")
                        + "  ·  API token  "
                        + (hasCloudflareToken() ? "設定済み" : "未設定"),
                11,
                AppUiTheme.APP_TEXT,
                true),
                matchTop(dp(7)));

        Button cloud = actionButton("Cloudflare認証設定");
        cloud.setOnClickListener(v -> showCloudflareCredentialsDialog());
        card.addView(cloud, matchTop(dp(7)));
        return card;
    }

    private void handleClefModelButton() {
        ClefLocalDownloadManager.Snapshot snapshot = ClefLocalDownloadManager.snapshot(this);
        if (snapshot.downloading) return;
        if (!snapshot.downloaded) {
            ClefLocalDownloadManager.startDownload(this);
            refreshClef();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("ローカル判断モデルを削除")
                .setMessage(
                        "約6.49 GBのClef-flashモデルを端末から削除します。"
                                + "\n\n分割脳の推論方式は変更しません。判断モデルを選択中の場合、"
                                + "再ダウンロードするまで明示的に実行エラーになります。通常LLMへ自動fallbackしません。")
                .setPositiveButton("削除", (dialog, which) -> {
                    if (!ClefLocalDownloadManager.deleteModel(this)) {
                        Toast.makeText(this, "判断モデルを削除できませんでした。", Toast.LENGTH_LONG).show();
                    }
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private View buildClefPerformanceCard() {
        LinearLayout card = card();
        card.addView(text("判断モデル 実測パフォーマンス", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "分割脳の判断モデルを実際に実行したときの処理時間とprocess memoryを記録します。"
                        + "測定用の追加推論は行いません。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        clefPerformanceStatus = text("", 11, AppUiTheme.APP_TEXT, false);
        clefPerformanceStatus.setLineSpacing(0f, 1.2f);
        card.addView(clefPerformanceStatus, matchTop(dp(8)));

        Button reset = actionButton("実測データをリセット");
        reset.setOnClickListener(v -> {
            clefPerformanceStore.clear();
            refreshClefPerformance();
        });
        card.addView(reset, matchTop(dp(8)));
        return card;
    }

    private void refreshClefPerformance() {
        if (clefPerformanceStatus == null) return;
        clefPerformanceStatus.setText(clefPerformanceStore.snapshot().displayText());
    }

    private void refreshClef() {
        ClefLocalDownloadManager.Snapshot snapshot = ClefLocalDownloadManager.snapshot(this);
        if (clefModelStatus != null) {
            String use = specialistInferenceSettingsStore.usesDecisionModel()
                    && DecisionModelCatalog.isLocal(decisionModelSettingsStore.model())
                    ? " · 分割脳で選択中"
                    : "";
            clefModelStatus.setText(snapshot.displayText() + use);
        }
        if (clefModelButton != null) {
            clefModelButton.setEnabled(!snapshot.downloading);
            clefModelButton.setText(snapshot.downloading
                    ? "ダウンロード中…"
                    : snapshot.downloaded ? "ローカル判断モデルを削除" : "ローカル判断モデルをダウンロード");
        }
        if (snapshot.downloading && clefModelStatus != null) {
            clefModelStatus.removeCallbacks(clefDownloadRefresh);
            clefModelStatus.postDelayed(clefDownloadRefresh, 750L);
        }
    }

    private final Runnable clefDownloadRefresh = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            refreshClef();
        }
    };

    private void rebuildContent() {
        if (isFinishing() || isDestroyed()) return;
        setContentView(buildContent());
        refresh();
    }

    private LinearLayout.LayoutParams matchTop(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
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
        refreshLocalModels();
        refreshClef();
        refreshClefPerformance();
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
                        rebuildContent();
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
                    rebuildContent();
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
