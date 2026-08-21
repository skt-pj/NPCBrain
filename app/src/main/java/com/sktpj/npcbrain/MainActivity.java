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

import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final class ModuleCard {
        final LinearLayout root;
        final TextView status;
        final TextView summary;
        final TextView facts;
        final TextView meta;

        ModuleCard(LinearLayout root, TextView status, TextView summary, TextView facts, TextView meta) {
            this.root = root;
            this.status = status;
            this.summary = summary;
            this.facts = facts;
            this.meta = meta;
        }
    }

    private static final class TraitControl {
        final LinearLayout root;
        final SeekBar seekBar;

        TraitControl(LinearLayout root, SeekBar seekBar) {
            this.root = root;
            this.seekBar = seekBar;
        }
    }

    private SecureApiKeyStore apiKeyStore;
    private MemoryStore memoryStore;
    private CharacterStateStore characterStateStore;
    private EditText inputView;
    private TextView statusView;
    private TextView outputView;
    private TextView memoryStatusView;
    private Button thinkButton;
    private ScrollView bodyScroll;
    private LinearLayout monitorContainer;
    private final Map<String, ModuleCard> moduleCards = new LinkedHashMap<>();
    private String activeStageId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        apiKeyStore = new SecureApiKeyStore(this);
        memoryStore = new MemoryStore(this);
        characterStateStore = new CharacterStateStore(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 252));
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(60));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("NPCBrain");
        title.setTextColor(Color.rgb(24, 24, 28));
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title);

        memoryStatusView = new TextView(this);
        memoryStatusView.setTextColor(Color.rgb(92, 92, 102));
        memoryStatusView.setTextSize(12);
        titleBox.addView(memoryStatusView);
        refreshHeaderStatus();

        Button menuButton = new Button(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(24);
        menuButton.setMinWidth(0);
        menuButton.setMinimumWidth(0);
        menuButton.setPadding(0, 0, 0, 0);
        menuButton.setContentDescription("人格・設定・記憶メニュー");
        menuButton.setOnClickListener(v -> showMenu(menuButton));
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        TextView model = new TextView(this);
        model.setText("GPT-5.6 Luna · reasoning MAX");
        model.setTextColor(Color.rgb(52, 67, 101));
        model.setTextSize(13);
        model.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modelParams.topMargin = dp(2);
        root.addView(model, modelParams);

        TextView description = new TextView(this);
        description.setText(
                "9つの専門領域＋Global Workspaceは固定したまま、設定した性格・価値観・目標・関係性を各領域の判断へ反映します。最終出力はAIの解説ではなくNPCの発話と行動です。");
        description.setTextColor(Color.rgb(74, 74, 84));
        description.setTextSize(13);
        description.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(6);
        root.addView(description, descriptionParams);

        inputView = new EditText(this);
        inputView.setHint("NPCが置かれた状況・起きた出来事・相手の発言など");
        inputView.setTextSize(16);
        inputView.setGravity(Gravity.TOP | Gravity.START);
        inputView.setMinLines(3);
        inputView.setMaxLines(5);
        inputView.setPadding(dp(12), dp(12), dp(12), dp(12));
        inputView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(10);
        root.addView(inputView, inputParams);

        thinkButton = new Button(this);
        thinkButton.setText("反応させる");
        thinkButton.setTextSize(16);
        thinkButton.setTypeface(Typeface.DEFAULT_BOLD);
        thinkButton.setContentDescription("入力した状況にNPCを反応させる");
        thinkButton.setOnClickListener(v -> startThinking());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        buttonParams.topMargin = dp(8);
        root.addView(thinkButton, buttonParams);

        statusView = new TextView(this);
        statusView.setText("待機中");
        statusView.setTextColor(Color.rgb(92, 92, 102));
        statusView.setTextSize(12);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(6);
        root.addView(statusView, statusParams);

        bodyScroll = new ScrollView(this);
        bodyScroll.setFillViewport(true);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(6), 0, dp(24));

        TextView monitorTitle = new TextView(this);
        monitorTitle.setText("脳内モニター");
        monitorTitle.setTextColor(Color.rgb(28, 28, 34));
        monitorTitle.setTextSize(18);
        monitorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(monitorTitle);

        TextView monitorNote = new TextView(this);
        monitorNote.setText(
                "各領域の公開用判断要約・人格影響・信頼度・注目事実を表示します。逐語的な内部思考やチェーン・オブ・ソートは表示・保存しません。");
        monitorNote.setTextColor(Color.rgb(96, 96, 106));
        monitorNote.setTextSize(12);
        monitorNote.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(3);
        body.addView(monitorNote, noteParams);

        monitorContainer = new LinearLayout(this);
        monitorContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams monitorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        monitorParams.topMargin = dp(8);
        body.addView(monitorContainer, monitorParams);
        createModuleCards();

        TextView resultTitle = new TextView(this);
        resultTitle.setText("NPCの反応");
        resultTitle.setTextColor(Color.rgb(28, 28, 34));
        resultTitle.setTextSize(18);
        resultTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams resultTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        resultTitleParams.topMargin = dp(18);
        body.addView(resultTitle, resultTitleParams);

        outputView = new TextView(this);
        outputView.setText("ここにNPCの発話と行動が表示されます。");
        outputView.setTextColor(Color.rgb(32, 32, 38));
        outputView.setTextSize(17);
        outputView.setLineSpacing(0f, 1.22f);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(dp(2), dp(8), dp(2), dp(24));
        body.addView(outputView);

        bodyScroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(2);
        root.addView(bodyScroll, scrollParams);

        return root;
    }

    private void createModuleCards() {
        moduleCards.clear();
        monitorContainer.removeAllViews();
        for (String stageId : BrainEngine.stageIds()) {
            ModuleCard card = createModuleCard(BrainEngine.stageLabel(stageId));
            moduleCards.put(stageId, card);
            monitorContainer.addView(card.root);
        }
    }

    private ModuleCard createModuleCard(String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(
                Color.rgb(246, 246, 249), Color.rgb(222, 222, 228)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(7);
        card.setLayoutParams(cardParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.rgb(38, 38, 44));
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setText("待機");
        state.setTextColor(Color.rgb(105, 105, 116));
        state.setTextSize(12);
        titleRow.addView(state);
        card.addView(titleRow);

        TextView summary = new TextView(this);
        summary.setText("まだ処理していません。");
        summary.setTextColor(Color.rgb(78, 78, 88));
        summary.setTextSize(14);
        summary.setLineSpacing(0f, 1.14f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(5);
        card.addView(summary, summaryParams);

        TextView facts = new TextView(this);
        facts.setText("");
        facts.setTextColor(Color.rgb(76, 76, 88));
        facts.setTextSize(12);
        facts.setLineSpacing(0f, 1.12f);
        facts.setVisibility(View.GONE);
        LinearLayout.LayoutParams factsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        factsParams.topMargin = dp(4);
        card.addView(facts, factsParams);

        TextView meta = new TextView(this);
        meta.setText("");
        meta.setTextColor(Color.rgb(110, 110, 120));
        meta.setTextSize(11);
        meta.setVisibility(View.GONE);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(4);
        card.addView(meta, metaParams);

        return new ModuleCard(card, state, summary, facts, meta);
    }

    private void resetBrainMonitor() {
        activeStageId = "";
        for (ModuleCard card : moduleCards.values()) {
            card.status.setText("待機");
            card.status.setTextColor(Color.rgb(105, 105, 116));
            card.summary.setText("まだ処理していません。");
            card.summary.setTextColor(Color.rgb(78, 78, 88));
            card.facts.setText("");
            card.facts.setVisibility(View.GONE);
            card.meta.setText("");
            card.meta.setVisibility(View.GONE);
            card.root.setBackground(cardBackground(
                    Color.rgb(246, 246, 249), Color.rgb(222, 222, 228)));
        }
    }

    private void markStageStarted(
            String stageId,
            String stageLabel,
            int current,
            int total
    ) {
        activeStageId = stageId;
        ModuleCard card = moduleCards.get(stageId);
        if (card == null) return;
        card.status.setText("思考中");
        card.status.setTextColor(Color.rgb(35, 93, 170));
        card.summary.setText("GPT-5.6 Lunaが状況・人格・記憶・前段の結果を処理しています…");
        card.summary.setTextColor(Color.rgb(49, 73, 105));
        card.facts.setVisibility(View.GONE);
        card.meta.setText(current + "/" + total + " · Luna MAX");
        card.meta.setVisibility(View.VISIBLE);
        card.root.setBackground(cardBackground(
                Color.rgb(237, 245, 255), Color.rgb(148, 185, 232)));
        statusView.setText(current + "/" + total + "  " + stageLabel);
        scrollToCard(card);
    }

    private void markStageCompleted(
            String stageId,
            int current,
            int total,
            String summary,
            double confidence,
            JSONArray salientFacts,
            String personalityEffect
    ) {
        ModuleCard card = moduleCards.get(stageId);
        if (card == null) return;
        card.status.setText("完了");
        card.status.setTextColor(Color.rgb(40, 116, 67));
        card.summary.setText(
                summary == null || summary.trim().isEmpty() ? "要約なし" : summary.trim());
        card.summary.setTextColor(Color.rgb(45, 45, 52));

        String factsText = formatFacts(salientFacts, personalityEffect);
        if (factsText.isEmpty()) {
            card.facts.setVisibility(View.GONE);
        } else {
            card.facts.setText(factsText);
            card.facts.setVisibility(View.VISIBLE);
        }

        int confidencePercent = (int) Math.round(
                Math.max(0.0, Math.min(1.0, confidence)) * 100.0);
        card.meta.setText(
                "信頼度 " + confidencePercent + "% · " + current + "/" + total + " · Luna MAX");
        card.meta.setVisibility(View.VISIBLE);
        card.root.setBackground(cardBackground(
                Color.rgb(241, 249, 243), Color.rgb(160, 207, 173)));
    }

    private void markActiveStageError(String message) {
        ModuleCard card = moduleCards.get(activeStageId);
        if (card == null) return;
        card.status.setText("停止");
        card.status.setTextColor(Color.rgb(176, 54, 54));
        card.summary.setText("この段階で処理を継続できませんでした。");
        card.summary.setTextColor(Color.rgb(110, 45, 45));
        card.facts.setText(limit(message, 320));
        card.facts.setVisibility(View.VISIBLE);
        card.root.setBackground(cardBackground(
                Color.rgb(255, 241, 241), Color.rgb(226, 165, 165)));
        scrollToCard(card);
    }

    private String formatFacts(JSONArray facts, String personalityEffect) {
        StringBuilder result = new StringBuilder();
        if (personalityEffect != null && !personalityEffect.trim().isEmpty()) {
            result.append("人格影響\n• ").append(personalityEffect.trim());
        }

        if (facts != null && facts.length() > 0) {
            if (result.length() > 0) result.append("\n\n");
            result.append("注目\n");
            int count = Math.min(3, facts.length());
            boolean wrote = false;
            for (int i = 0; i < count; i++) {
                String fact = facts.optString(i, "").trim();
                if (fact.isEmpty()) continue;
                if (wrote) result.append('\n');
                result.append("• ").append(fact);
                wrote = true;
            }
        }
        return result.toString().trim();
    }

    private void scrollToCard(ModuleCard card) {
        bodyScroll.post(() -> bodyScroll.smoothScrollTo(
                0, Math.max(0, card.root.getTop() - dp(48))));
    }

    private GradientDrawable cardBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("人格設定");
        popup.getMenu().add("OpenAI APIキー");
        popup.getMenu().add("記憶を見る");
        popup.getMenu().add("記憶を消去");
        popup.setOnMenuItemClickListener(item -> {
            if ("人格設定".contentEquals(item.getTitle())) {
                showPersonalityDialog();
            } else if ("OpenAI APIキー".contentEquals(item.getTitle())) {
                showApiKeyDialog();
            } else if ("記憶を見る".contentEquals(item.getTitle())) {
                showMemoryDialog();
            } else if ("記憶を消去".contentEquals(item.getTitle())) {
                confirmClearMemory();
            }
            return true;
        });
        popup.show();
    }

    private void showPersonalityDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(16));

        EditText name = addTextField(
                form, "名前", characterStateStore.displayName(), true, 1);
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
                characterStateStore.traitPercent(CharacterStateStore.extraversionKey()));
        TraitControl neuroticism = createTraitControl(
                "神経症傾向",
                characterStateStore.traitPercent(CharacterStateStore.neuroticismKey()));
        TraitControl agreeableness = createTraitControl(
                "協調性",
                characterStateStore.traitPercent(CharacterStateStore.agreeablenessKey()));
        TraitControl conscientiousness = createTraitControl(
                "誠実性",
                characterStateStore.traitPercent(CharacterStateStore.conscientiousnessKey()));
        TraitControl openness = createTraitControl(
                "開放性",
                characterStateStore.traitPercent(CharacterStateStore.opennessKey()));

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
                form, "話し方", characterStateStore.speechStyle(), false, 2);

        TextView currentState = new TextView(this);
        currentState.setText("現在状態: " + characterStateStore.dynamicStateSummary());
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
                .setTitle("人格設定")
                .setMessage("性格は別の脳を追加せず、既存9領域の注意・記憶検索・価値判断・行動選択などへ横断的に反映します。")
                .setView(scroll)
                .setPositiveButton("保存", (dialog, which) -> {
                    characterStateStore.saveProfile(
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
                    refreshHeaderStatus();
                    Toast.makeText(this, "人格設定を保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("標準に戻す", (dialog, which) -> {
                    characterStateStore.reset();
                    memoryStore.clearProfileAdaptations();
                    refreshHeaderStatus();
                    Toast.makeText(this, "人格設定を標準に戻しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
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

    private void showMemoryDialog() {
        TextView memoryText = new TextView(this);
        memoryText.setText(memoryStore.preview());
        memoryText.setTextSize(15);
        memoryText.setTextColor(Color.rgb(32, 32, 38));
        memoryText.setTextIsSelectable(true);
        int pad = dp(20);
        memoryText.setPadding(pad, dp(8), pad, dp(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(memoryText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("長期記憶")
                .setMessage("エピソード記憶は経験、意味記憶は世界知識と人物固有の目標・価値観・関係性などを型付きで保持します。")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void confirmClearMemory() {
        new AlertDialog.Builder(this)
                .setTitle("記憶を消去")
                .setMessage("経験と学習した意味記憶を削除します。人格設定で指定した性格・価値観・目標・関係性とAPIキーは残します。")
                .setPositiveButton("消去", (dialog, which) -> {
                    memoryStore.clear();
                    refreshHeaderStatus();
                    Toast.makeText(this, "学習した長期記憶を消去しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void refreshHeaderStatus() {
        if (memoryStatusView == null || memoryStore == null || characterStateStore == null) return;
        MemoryStore.Stats stats = memoryStore.stats();
        memoryStatusView.setText(
                "人物 " + characterStateStore.displayName()
                        + " · 記憶 E " + stats.episodes
                        + " · S " + stats.semantics);
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
                .setMessage("GPT-5.6 LunaをOpenAI Responses APIで使用します。APIキーは端末のAndroid Keystoreで暗号化して保存し、長期記憶には保存しません。")
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
                        Toast.makeText(
                                this,
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

    private void startThinking() {
        String input = inputView.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "状況を入力してください", Toast.LENGTH_SHORT).show();
            return;
        }

        final String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            showError("APIキーを読み出せません: " + error.getMessage());
            return;
        }
        if (apiKey.isEmpty()) {
            showApiKeyDialog();
            return;
        }

        hideKeyboard();
        resetBrainMonitor();
        thinkButton.setEnabled(false);
        inputView.setEnabled(false);
        outputView.setText(
                characterStateStore.displayName() + " の反応をGPT-5.6 Lunaで統合しています…");
        statusView.setText("Luna MAXで認知サイクル開始");

        new Thread(() -> {
            try {
                BrainEngine engine = new BrainEngine(
                        new OpenAiClient(getApplicationContext(), apiKey),
                        memoryStore,
                        characterStateStore
                );
                String result = engine.think(input, new BrainEngine.ProgressListener() {
                    @Override
                    public void onStageStarted(
                            String stageId,
                            String stageLabel,
                            int current,
                            int total
                    ) {
                        runOnUiThread(() ->
                                markStageStarted(stageId, stageLabel, current, total));
                    }

                    @Override
                    public void onStageCompleted(
                            String stageId,
                            String stageLabel,
                            int current,
                            int total,
                            String summary,
                            double confidence,
                            JSONArray salientFacts,
                            String personalityEffect
                    ) {
                        runOnUiThread(() -> markStageCompleted(
                                stageId,
                                current,
                                total,
                                summary,
                                confidence,
                                salientFacts,
                                personalityEffect
                        ));
                    }
                });
                runOnUiThread(() -> {
                    outputView.setText(result);
                    statusView.setText(
                            "完了 · " + characterStateStore.displayName() + " · Luna MAX");
                    refreshHeaderStatus();
                    thinkButton.setEnabled(true);
                    inputView.setEnabled(true);
                    bodyScroll.post(() -> bodyScroll.fullScroll(View.FOCUS_DOWN));
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.toString()
                        : error.getMessage();
                runOnUiThread(() -> {
                    markActiveStageError(message);
                    showError(message);
                    thinkButton.setEnabled(true);
                    inputView.setEnabled(true);
                });
            }
        }, "npcbrain-think").start();
    }

    private void hideKeyboard() {
        inputView.clearFocus();
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
        }
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
        final int horizontal = dp(16);
        final int topBase = dp(6);
        final int bottomBase = dp(10);

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

    private void showError(String message) {
        statusView.setText("エラー");
        outputView.setText(message);
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
