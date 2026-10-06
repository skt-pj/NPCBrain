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
    private SecureTypeSafeApiKeyStore typeSafeApiKeyStore;
    private ModelSettingsStore modelSettingsStore;
    private RoutingSettingsStore routingSettingsStore;
    private LocalInferenceSettingsStore localInferenceSettingsStore;
    private SpecialistInferenceSettingsStore specialistInferenceSettingsStore;
    private DecisionModelSettingsStore decisionModelSettingsStore;
    private ClefSettingsStore clefSettingsStore;
    private ClefPerformanceStore clefPerformanceStore;
    private NpcRegistryStore registryStore;
    private NpcAiStaminaStore staminaStore;
    private TextView apiKeyStatus;
    private TextView typeSafeApiKeyStatus;
    private TextView clefModelStatus;
    private Button clefModelButton;
    private TextView clefPerformanceStatus;
    private Button clefProbeButton;
    private TextView clefProbeStatus;
    private volatile boolean clefProbeRunning;
    private LinearLayout localModelsContainer;
    private LinearLayout budgetContainer;
    private Button cacheProbeButton;
    private TextView cacheProbeStatus;
    private volatile boolean cacheProbeRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        typeSafeApiKeyStore = new SecureTypeSafeApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        routingSettingsStore = new RoutingSettingsStore(this);
        localInferenceSettingsStore = new LocalInferenceSettingsStore(this);
        specialistInferenceSettingsStore = new SpecialistInferenceSettingsStore(this);
        decisionModelSettingsStore = new DecisionModelSettingsStore(this);
        clefSettingsStore = new ClefSettingsStore(this);
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
                "実行場所 → モデル → 詳細の順で共通AI構成を設定します。NPC個別の上書きはDEBUGのNPC管理で行います。",
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

        if (isDebuggableBuild() && shouldShowOpenAiPromptCacheProbe()) {
            LinearLayout.LayoutParams cacheParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cacheParams.topMargin = dp(12);
            body.addView(buildPromptCacheDebugCard(), cacheParams);
        }

        TextView budgetTitle = text("NPC別 API費用", 18, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams budgetTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        budgetTitleParams.topMargin = dp(18);
        body.addView(budgetTitle, budgetTitleParams);

        TextView budgetNote = text(
                "上限は各NPCに紐づく有料クラウドAI利用全体へ適用されます。リセットしても累計は残ります。",
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
                routeSummary(routingSettingsStore.globalModel()));
        addConfigurationRow(
                card,
                "分割脳（9専門）",
                specialistInferenceSettingsStore.usesDecisionModel()
                        ? (decisionModelSettingsStore.usesCloudJev()
                        ? "判断モデル / クラウド / TypeSafe / Jev"
                        : "判断モデル / ローカル / CLEF-Flash 9B Q4_K_M / "
                        + ClefSettingsStore.executionBackendLabel(
                                clefSettingsStore.executionBackend()))
                        : "通常LLM / " + routeSummary(routingSettingsStore.specialistModel()));
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
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f));
        TextView detail = text(value, 11, AppUiTheme.APP_MUTED, false);
        detail.setGravity(Gravity.END);
        row.addView(detail,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.58f));
    }

    private String routeSummary(String model) {
        return NpcInferenceModel.executionLocationLabel(model)
                + " / " + NpcInferenceModel.displayLabel(model);
    }

    private View buildRoutingCard() {
        LinearLayout card = card();
        card.addView(text("脳への割り当て", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "Global Workspaceは通常LLM固定。分割脳9専門は通常LLMか判断モデルのどちらか一方を使います。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        addRoutingGroup(card, "Global Workspace", true);
        addSpecialistInferenceModeGroup(card);
        if (!specialistInferenceSettingsStore.usesDecisionModel()) {
            addRoutingGroup(card, "分割脳（通常LLM）", false);
        }
        return card;
    }

    private void addRoutingGroup(
            LinearLayout card,
            String title,
            boolean global
    ) {
        String current = global
                ? routingSettingsStore.globalModel()
                : routingSettingsStore.specialistModel();

        TextView heading = text(title, 15, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(16);
        card.addView(heading, headingParams);

        TextView locationTitle = text("① 実行場所", 12, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams locationTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        locationTitleParams.topMargin = dp(8);
        card.addView(locationTitle, locationTitleParams);

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

        TextView modelTitle = text("② モデル", 12, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams modelTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modelTitleParams.topMargin = dp(8);
        card.addView(modelTitle, modelTitleParams);

        Button modelButton = actionButton(NpcInferenceModel.displayLabel(current) + "  ▼");
        modelButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        modelButton.setPadding(dp(12), 0, dp(12), 0);
        modelButton.setOnClickListener(v -> showModelPicker(global));
        card.addView(modelButton, matchTop(dp(5)));

        TextView detailTitle = text("③ 詳細", 12, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams detailTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailTitleParams.topMargin = dp(9);
        card.addView(detailTitle, detailTitleParams);

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
            if (NpcInferenceModel.isLocal(current)) {
                showLocalDetailDialog(title, current);
            } else {
                showOpenAiDetailDialog(title + " 詳細設定");
            }
        });
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
        AppDialog.builder(this)
                .setTitle(local ? "ローカルモデルを選択" : "クラウドモデルを選択")
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
                "この設定はGlobal Workspace / Specialist Brainで同じローカルruntimeを共有します。",
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

        AppDialog.builder(this)
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
                "このReasoning設定はOpenAIを選択したGlobal Workspace / Specialist Brainで共通利用します。"
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

        AppDialog.builder(this)
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

    private void addSpecialistInferenceModeGroup(LinearLayout card) {
        TextView heading = text("分割脳（9専門）", 15, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(18);
        card.addView(heading, headingParams);
        card.addView(text(
                "9専門すべてで同じ推論カテゴリを使います。判断モデルは固定反応信号を返し、詳細な解釈と最終判断はGlobal Workspaceが行います。",
                10,
                AppUiTheme.APP_MUTED,
                false));

        RadioGroup group = new RadioGroup(this);
        RadioButton normal = routeRadio("通常LLM");
        RadioButton localDecision = routeRadio("判断モデル（ローカル CLEF-Flash）");
        RadioButton cloudDecision = routeRadio("判断モデル（クラウド Jev）");
        int normalId = View.generateViewId();
        int localDecisionId = View.generateViewId();
        int cloudDecisionId = View.generateViewId();
        normal.setId(normalId);
        localDecision.setId(localDecisionId);
        cloudDecision.setId(cloudDecisionId);
        group.addView(normal);
        group.addView(localDecision);
        group.addView(cloudDecision);

        int initialCheckedId = !specialistInferenceSettingsStore.usesDecisionModel()
                ? normalId
                : decisionModelSettingsStore.usesCloudJev()
                ? cloudDecisionId
                : localDecisionId;
        group.check(initialCheckedId);

        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            if (checkedId == normalId) {
                if (specialistInferenceSettingsStore.usesDecisionModel()) {
                    specialistInferenceSettingsStore.setMode(
                            SpecialistInferenceSettingsStore.MODE_NORMAL_LLM);
                    rebuildContent();
                }
                return;
            }

            if (checkedId == cloudDecisionId) {
                if (specialistInferenceSettingsStore.usesDecisionModel()
                        && decisionModelSettingsStore.usesCloudJev()) {
                    return;
                }
                if (!hasTypeSafeApiKey()) {
                    radioGroup.check(initialCheckedId);
                    showTypeSafeApiKeyDialog(() -> {
                        decisionModelSettingsStore.setRoute(
                                DecisionModelSettingsStore.ROUTE_CLOUD_JEV);
                        specialistInferenceSettingsStore.setMode(
                                SpecialistInferenceSettingsStore.MODE_DECISION_MODEL);
                        rebuildContent();
                    });
                    return;
                }
                decisionModelSettingsStore.setRoute(
                        DecisionModelSettingsStore.ROUTE_CLOUD_JEV);
                specialistInferenceSettingsStore.setMode(
                        SpecialistInferenceSettingsStore.MODE_DECISION_MODEL);
                rebuildContent();
                return;
            }

            if (specialistInferenceSettingsStore.usesDecisionModel()
                    && decisionModelSettingsStore.usesLocalClef()) {
                return;
            }
            ClefLocalDownloadManager.Snapshot snapshot =
                    ClefLocalDownloadManager.snapshot(this);
            if (!snapshot.downloaded) {
                radioGroup.check(initialCheckedId);
                AppDialog.builder(this)
                        .setTitle("CLEF-Flashが未ダウンロードです")
                        .setMessage("ローカルCLEF-Flashを使用するにはモデルをダウンロードしてください。")
                        .setPositiveButton("ダウンロード", (dialog, which) -> {
                            ClefLocalDownloadManager.startDownload(this);
                            refreshClef();
                        })
                        .setNegativeButton("キャンセル", null)
                        .show();
                return;
            }
            if (clefSettingsStore.executionBackend().isEmpty()) {
                radioGroup.check(initialCheckedId);
                showClefExecutionBackendPicker(() -> {
                    decisionModelSettingsStore.setRoute(
                            DecisionModelSettingsStore.ROUTE_LOCAL_CLEF);
                    specialistInferenceSettingsStore.setMode(
                            SpecialistInferenceSettingsStore.MODE_DECISION_MODEL);
                    rebuildContent();
                });
                return;
            }
            decisionModelSettingsStore.setRoute(
                    DecisionModelSettingsStore.ROUTE_LOCAL_CLEF);
            specialistInferenceSettingsStore.setMode(
                    SpecialistInferenceSettingsStore.MODE_DECISION_MODEL);
            rebuildContent();
        });
        card.addView(group, matchTop(dp(7)));

        if (specialistInferenceSettingsStore.usesDecisionModel()) {
            if (decisionModelSettingsStore.usesCloudJev()) {
                card.addView(text(
                        "実行場所  クラウド"
                                + "\nProvider  TypeSafe"
                                + "\nモデル  Jev · jev-latest"
                                + "\n処理  9専門を並列実行"
                                + "\nAPIキー  " + (hasTypeSafeApiKey() ? "設定済み" : "未設定"),
                        10,
                        AppUiTheme.APP_MUTED,
                        false),
                        matchTop(dp(6)));
                Button keyButton = actionButton("TypeSafe APIキー設定");
                keyButton.setOnClickListener(v -> showTypeSafeApiKeyDialog());
                card.addView(keyButton, matchTop(dp(6)));
            } else {
                ClefLocalDownloadManager.Snapshot snapshot =
                        ClefLocalDownloadManager.snapshot(this);
                String backend = clefSettingsStore.executionBackend();
                card.addView(text(
                        "実行場所  ローカル"
                                + "\nモデル  CLEF-Flash 9B · Q4_K_M"
                                + "\n実行方式  " + ClefSettingsStore.executionBackendLabel(backend)
                                + "\n処理  9専門を独立キュー項目としてFIFO実行"
                                + "\ncontext  2048 tokens内へgrounded snapshotを自動圧縮"
                                + "\n状態  " + snapshot.displayText(),
                        10,
                        AppUiTheme.APP_MUTED,
                        false),
                        matchTop(dp(6)));
                Button backendButton = actionButton(
                        backend.isEmpty() ? "実行方式を選択" : "実行方式を変更");
                backendButton.setOnClickListener(
                        v -> showClefExecutionBackendPicker(this::rebuildContent));
                card.addView(backendButton, matchTop(dp(6)));
            }
        }
    }

    private View buildLocalModelsCard() {
        LinearLayout card = card();
        card.addView(text("ローカルモデル管理", 18, AppUiTheme.APP_TEXT, true));
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
        card.addView(text("クラウド設定", 18, AppUiTheme.APP_TEXT, true));

        card.addView(text("Provider  OpenAI", 12, AppUiTheme.APP_TEXT, true), matchTop(dp(7)));
        card.addView(text("モデル  GPT-6 Luna / GPT-5.6 Luna", 11, AppUiTheme.APP_MUTED, false));
        card.addView(text(
                "Reasoning  " + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort()),
                11,
                AppUiTheme.APP_MUTED,
                false));
        apiKeyStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        card.addView(apiKeyStatus, matchTop(dp(6)));

        LinearLayout openAiActions = new LinearLayout(this);
        openAiActions.setOrientation(LinearLayout.HORIZONTAL);
        Button details = actionButton("OpenAI共通詳細");
        details.setOnClickListener(v -> showOpenAiDetailDialog("OpenAI 共通設定"));
        openAiActions.addView(details, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button clearKey = actionButton("APIキー削除");
        clearKey.setOnClickListener(v -> confirmClearApiKey());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(104), dp(44));
        clearParams.leftMargin = dp(6);
        openAiActions.addView(clearKey, clearParams);
        card.addView(openAiActions, matchTop(dp(8)));

        card.addView(text("Provider  TypeSafe", 12, AppUiTheme.APP_TEXT, true), matchTop(dp(16)));
        card.addView(text("モデル  Jev · jev-latest", 11, AppUiTheme.APP_MUTED, false));
        typeSafeApiKeyStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        card.addView(typeSafeApiKeyStatus, matchTop(dp(6)));

        LinearLayout typeSafeActions = new LinearLayout(this);
        typeSafeActions.setOrientation(LinearLayout.HORIZONTAL);
        Button setTypeSafeKey = actionButton("TypeSafe APIキー設定");
        setTypeSafeKey.setOnClickListener(v -> showTypeSafeApiKeyDialog());
        typeSafeActions.addView(setTypeSafeKey, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button clearTypeSafeKey = actionButton("APIキー削除");
        clearTypeSafeKey.setOnClickListener(v -> confirmClearTypeSafeApiKey());
        LinearLayout.LayoutParams typeSafeClearParams =
                new LinearLayout.LayoutParams(dp(104), dp(44));
        typeSafeClearParams.leftMargin = dp(6);
        typeSafeActions.addView(clearTypeSafeKey, typeSafeClearParams);
        card.addView(typeSafeActions, matchTop(dp(8)));
        return card;
    }

    private View buildClefSettingsCard() {
        LinearLayout card = card();
        card.addView(text("CLEF-Flash 共通設定", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "CLEF-Flashは分割脳9専門の判断モデルとして使います。各専門は固定の低レベル反応だけを返します。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        TextView model = text(
                "CLEF-Flash 9B · Q4_K_M · 約6.49 GB",
                12,
                AppUiTheme.APP_TEXT,
                true);
        card.addView(model, matchTop(dp(9)));
        card.addView(text(
                "ggml-org/Clef-Flash-GGUF · architecture=clef / joint head込み",
                10,
                AppUiTheme.APP_MUTED,
                false));

        Button backend = actionButton(
                "実行方式: "
                        + ClefSettingsStore.executionBackendLabel(clefSettingsStore.executionBackend())
                        + "  ▼");
        backend.setOnClickListener(v -> showClefExecutionBackendPicker(this::rebuildContent));
        card.addView(backend, matchTop(dp(8)));

        clefModelStatus = text("", 12, AppUiTheme.APP_TEXT, true);
        card.addView(clefModelStatus, matchTop(dp(8)));

        clefModelButton = actionButton("");
        clefModelButton.setOnClickListener(v -> handleClefModelButton());
        card.addView(clefModelButton, matchTop(dp(7)));
        return card;
    }

    private void showClefExecutionBackendPicker(Runnable onSaved) {
        String current = clefSettingsStore.executionBackend();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(AppUiTheme.APP_SURFACE);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        content.addView(text(
                "使用する実行方式を選択してください。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        RadioGroup backendGroup = new RadioGroup(this);
        backendGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton gpu = routeRadio("GPU");
        RadioButton cpu = routeRadio("CPU");
        int gpuId = View.generateViewId();
        int cpuId = View.generateViewId();
        gpu.setId(gpuId);
        cpu.setId(cpuId);
        backendGroup.addView(gpu, new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, dp(48)));
        backendGroup.addView(cpu, new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, dp(48)));
        if (ClefSettingsStore.EXECUTION_GPU.equals(current)) {
            backendGroup.check(gpuId);
        } else if (ClefSettingsStore.EXECUTION_CPU.equals(current)) {
            backendGroup.check(cpuId);
        }
        content.addView(backendGroup, matchTop(dp(8)));

        AlertDialog dialog = AppDialog.builder(this)
                .setTitle("CLEF-Flash 実行方式")
                .setView(content)
                .setPositiveButton("保存", null)
                .setNegativeButton("キャンセル", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int selected = backendGroup.getCheckedRadioButtonId();
                    if (selected != gpuId && selected != cpuId) {
                        Toast.makeText(this, "GPUまたはCPUを選択してください。", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    clefSettingsStore.setExecutionBackend(
                            selected == gpuId
                                    ? ClefSettingsStore.EXECUTION_GPU
                                    : ClefSettingsStore.EXECUTION_CPU);
                    dialog.dismiss();
                    if (onSaved != null) onSaved.run();
                }));
        dialog.show();
    }

    private void handleClefModelButton() {
        ClefLocalDownloadManager.Snapshot snapshot = ClefLocalDownloadManager.snapshot(this);
        if (snapshot.downloading) return;
        if (!snapshot.downloaded) {
            ClefLocalDownloadManager.startDownload(this);
            refreshClef();
            return;
        }
        AppDialog.builder(this)
                .setTitle("CLEF-Flashモデルを削除")
                .setMessage("約6.49 GBのCLEF-Flashモデルを端末から削除します。")
                .setPositiveButton("削除", (dialog, which) -> {
                    if (specialistInferenceSettingsStore.usesDecisionModel()
                            && decisionModelSettingsStore.usesLocalClef()) {
                        specialistInferenceSettingsStore.setMode(
                                SpecialistInferenceSettingsStore.MODE_NORMAL_LLM);
                    }
                    if (!ClefLocalDownloadManager.deleteModel(this)) {
                        Toast.makeText(this, "CLEFモデルを削除できませんでした。", Toast.LENGTH_LONG).show();
                    }
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private View buildClefPerformanceCard() {
        LinearLayout card = card();
        card.addView(text("CLEF 実測パフォーマンス", 18, AppUiTheme.APP_TEXT, true));
        card.addView(text(
                "通常の分割脳実行は自動記録します。下の実測ボタンはOpenAIを一切使わず、端末内CLEFで固定9専門を1回ずつ実推論して処理時間とprocess memoryを測ります。",
                11,
                AppUiTheme.APP_MUTED,
                false));

        clefPerformanceStatus = text("", 11, AppUiTheme.APP_TEXT, false);
        clefPerformanceStatus.setLineSpacing(0f, 1.2f);
        card.addView(clefPerformanceStatus, matchTop(dp(8)));

        clefProbeButton = actionButton("ローカルCLEF 9専門を実測");
        clefProbeButton.setOnClickListener(v -> startClefPerformanceProbe());
        card.addView(clefProbeButton, matchTop(dp(8)));

        clefProbeStatus = text(
                "未実行。APIキー不要・外部通信なし。",
                10,
                AppUiTheme.APP_MUTED,
                false);
        clefProbeStatus.setLineSpacing(0f, 1.15f);
        card.addView(clefProbeStatus, matchTop(dp(5)));

        Button reset = actionButton("実測データをリセット");
        reset.setOnClickListener(v -> {
            clefPerformanceStore.clear();
            refreshClefPerformance();
        });
        card.addView(reset, matchTop(dp(8)));
        return card;
    }

    private void refreshClefPerformance() {
        if (clefPerformanceStatus != null) {
            clefPerformanceStatus.setText(clefPerformanceStore.snapshot().displayText());
        }
        if (clefProbeButton != null) {
            ClefLocalDownloadManager.Snapshot snapshot =
                    ClefLocalDownloadManager.snapshot(this);
            boolean backendSelected = !clefSettingsStore.executionBackend().isEmpty();
            clefProbeButton.setEnabled(snapshot.downloaded && backendSelected && !clefProbeRunning);
            if (!snapshot.downloaded && clefProbeStatus != null && !clefProbeRunning) {
                clefProbeStatus.setText("CLEF-Flashをダウンロードすると実測できます。APIキーは不要です。");
            } else if (!backendSelected && clefProbeStatus != null && !clefProbeRunning) {
                clefProbeStatus.setText("実行方式が未選択です。GPUまたはCPUを選択してください。");
            }
        }
    }

    private void startClefPerformanceProbe() {
        if (clefProbeRunning) return;
        ClefLocalDownloadManager.Snapshot snapshot =
                ClefLocalDownloadManager.snapshot(this);
        if (!snapshot.downloaded) {
            if (clefProbeStatus != null) {
                clefProbeStatus.setText("CLEF-Flashが未ダウンロードです。APIキーは不要です。");
            }
            return;
        }

        clefProbeRunning = true;
        if (clefProbeButton != null) clefProbeButton.setEnabled(false);
        if (clefProbeStatus != null) {
            clefProbeStatus.setText("実測中… 端末内CLEFで9専門を順番に実推論しています。");
        }

        new Thread(() -> {
            try {
                ClefPerformanceProbe.Result result = ClefPerformanceProbe.run(
                        this,
                        (moduleId, completed, total, durationMs) -> runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed() || clefProbeStatus == null) return;
                            clefProbeStatus.setText(
                                    "実測中… " + completed + "/" + total
                                            + "  " + BrainEngine.stageLabel(moduleId)
                                            + "  " + durationMs + " ms"
                                            + "\n実行場所  ローカル · モデル  "
                                            + ClefSettingsStore.displayLabel()
                                            + "\n実行方式  "
                                            + ClefSettingsStore.executionBackendLabel(
                                                    clefSettingsStore.executionBackend())
                                            + "\nAPIキー不要・外部通信なし。");
                        }));
                runOnUiThread(() -> finishClefPerformanceProbe(result.displayText()));
            } catch (Exception error) {
                String detail = ProcessingQueueRegistry.rootMessage(error);
                runOnUiThread(() -> finishClefPerformanceProbe("ERROR: " + detail));
            }
        }, "clef-local-performance-probe").start();
    }

    private void finishClefPerformanceProbe(String result) {
        clefProbeRunning = false;
        if (isFinishing() || isDestroyed()) return;
        if (clefProbeStatus != null) {
            clefProbeStatus.setText(result == null ? "" : result);
        }
        refreshClefPerformance();
    }

    private void refreshClef() {
        ClefLocalDownloadManager.Snapshot snapshot = ClefLocalDownloadManager.snapshot(this);
        if (!snapshot.downloaded
                && specialistInferenceSettingsStore.usesDecisionModel()
                && decisionModelSettingsStore.usesLocalClef()) {
            specialistInferenceSettingsStore.setMode(
                    SpecialistInferenceSettingsStore.MODE_NORMAL_LLM);
        }
        if (clefModelStatus != null) {
            clefModelStatus.setText(snapshot.displayText()
                    + " · 実行方式 "
                    + ClefSettingsStore.executionBackendLabel(clefSettingsStore.executionBackend())
                    + (specialistInferenceSettingsStore.usesDecisionModel()
                    && decisionModelSettingsStore.usesLocalClef()
                    ? " · 分割脳9専門で使用中" : ""));
        }
        if (clefModelButton != null) {
            clefModelButton.setEnabled(!snapshot.downloading);
            clefModelButton.setText(snapshot.downloading
                    ? "ダウンロード中…"
                    : snapshot.downloaded ? "CLEFモデルを削除" : "CLEFモデルをダウンロード");
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

    private boolean usesAnyOpenAiRoute() {
        if (NpcInferenceModel.usesOpenAi(routingSettingsStore.globalModel())) return true;
        if (!specialistInferenceSettingsStore.usesDecisionModel()
                && NpcInferenceModel.usesOpenAi(routingSettingsStore.specialistModel())) {
            return true;
        }
        for (String npcId : registryStore.npcIds()) {
            if (NpcInferenceAccess.usesOpenAi(this, npcId)) return true;
        }
        return false;
    }

    private boolean usesJevRoute() {
        return specialistInferenceSettingsStore.usesDecisionModel()
                && decisionModelSettingsStore.usesCloudJev();
    }

    private boolean usesBillableCloudForNpc(String npcId) {
        return usesJevRoute() || NpcInferenceAccess.usesOpenAi(this, npcId);
    }

    private boolean shouldShowOpenAiPromptCacheProbe() {
        if (specialistInferenceSettingsStore.usesDecisionModel()) return false;
        if (NpcInferenceModel.usesOpenAi(routingSettingsStore.specialistModel())) return true;
        for (String npcId : registryStore.npcIds()) {
            if (NpcInferenceModel.usesOpenAi(
                    new NpcModelStore(this, npcId).effectiveSpecialistModel())) {
                return true;
            }
        }
        return false;
    }

    private View buildPromptCacheDebugCard() {
        LinearLayout card = card();
        card.addView(text("OpenAI Prompt Cache Test", 18, AppUiTheme.APP_TEXT, true));

        TextView note = text(
                "Debug専用・通常LLMの分割脳がOpenAIを使う場合だけ表示します。1専門をwarm-up後、残り8専門を実API実行してPrompt Cacheを測定します。CLEFの性能測定ではありません。",
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
            if (!usesAnyOpenAiRoute()) {
                apiKeyStatus.setText("OpenAI APIキー  現在の選択中ルートでは不要");
            } else {
                apiKeyStatus.setText(hasApiKey()
                        ? "OpenAI APIキー  設定済み（値は非表示）"
                        : "OpenAI APIキー  必要・未設定");
            }
        }
        if (typeSafeApiKeyStatus != null) {
            if (!usesJevRoute()) {
                typeSafeApiKeyStatus.setText("TypeSafe APIキー  現在の選択中ルートでは不要");
            } else {
                typeSafeApiKeyStatus.setText(hasTypeSafeApiKey()
                        ? "TypeSafe APIキー  設定済み（値は非表示）"
                        : "TypeSafe APIキー  必要・未設定");
            }
        }
        renderBudgetCards();
    }

    private void renderBudgetCards() {
        if (budgetContainer == null) return;
        budgetContainer.removeAllViews();
        List<String> ids = registryStore.npcIds();
        int shown = 0;
        for (String npcId : ids) {
            if (!usesBillableCloudForNpc(npcId)) continue;
            budgetContainer.addView(buildBudgetCard(npcId));
            shown++;
        }
        if (shown == 0) {
            budgetContainer.addView(text(
                    "有料クラウドルートを選択中のNPCはいません。",
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
        AppDialog.builder(this)
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
        AppDialog.builder(this)
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

    private void showTypeSafeApiKeyDialog() {
        showTypeSafeApiKeyDialog(this::rebuildContent);
    }

    private void showTypeSafeApiKeyDialog(Runnable onSaved) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("TypeSafe API key");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = dp(20);
        input.setPadding(pad, dp(6), pad, dp(6));
        AppDialog.builder(this)
                .setTitle("TypeSafe APIキー")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    try {
                        typeSafeApiKeyStore.save(value);
                        if (onSaved != null) onSaved.run();
                    } catch (Exception error) {
                        Toast.makeText(this, "APIキー保存失敗", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void confirmClearTypeSafeApiKey() {
        AppDialog.builder(this)
                .setTitle("TypeSafe APIキーを削除")
                .setMessage("保存済みのTypeSafe APIキーを削除します。")
                .setPositiveButton("削除", (dialog, which) -> {
                    typeSafeApiKeyStore.clear();
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void confirmClearApiKey() {
        AppDialog.builder(this)
                .setTitle("APIキーを削除")
                .setMessage("保存済みのOpenAI APIキーを削除します。")
                .setPositiveButton("削除", (dialog, which) -> {
                    apiKeyStore.clear();
                    rebuildContent();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private boolean hasTypeSafeApiKey() {
        try {
            return !typeSafeApiKeyStore.load().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
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