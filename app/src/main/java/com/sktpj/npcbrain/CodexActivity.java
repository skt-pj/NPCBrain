package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public final class CodexActivity extends Activity {
    private static final int BG_TOP = Color.rgb(5, 8, 14);
    private static final int BG_BOTTOM = Color.rgb(10, 18, 29);
    private static final int CARD = Color.rgb(16, 25, 38);
    private static final int CARD_ALT = Color.rgb(20, 31, 46);
    private static final int BORDER = Color.rgb(50, 68, 88);
    private static final int TEXT = Color.rgb(238, 242, 247);
    private static final int MUTED = Color.rgb(145, 158, 176);
    private static final int ACCENT = Color.rgb(212, 178, 108);
    private static final int ACCENT_DARK = Color.rgb(79, 63, 37);

    private NpcArchiveStore archiveStore;
    private String selectedNpcId = "npc1";
    private LinearLayout archiveContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        archiveStore = new NpcArchiveStore(this);
        setContentView(buildContent());
        renderArchive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderArchive();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(backgroundGradient());
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(4), dp(2), dp(10));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("NPC ARCHIVE", 10, ACCENT, true);
        eyebrow.setLetterSpacing(0.18f);
        heading.addView(eyebrow);
        TextView title = text("図鑑", 27, TEXT, true);
        heading.addView(title);
        header.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView mark = text("◆", 24, ACCENT, true);
        mark.setGravity(Gravity.CENTER);
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button conversation = tabButton("会話", false);
        conversation.setOnClickListener(v -> openConversation());
        tabs.addView(conversation, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button status = tabButton("NPC状況", false);
        status.setOnClickListener(v -> openNpcStatus());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        statusParams.leftMargin = dp(4);
        tabs.addView(status, statusParams);

        Button dungeon = tabButton("ダンジョン", false);
        dungeon.setOnClickListener(v -> openDungeon());
        LinearLayout.LayoutParams dungeonParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        dungeonParams.leftMargin = dp(4);
        tabs.addView(dungeon, dungeonParams);

        Button codex = tabButton("図鑑", true);
        codex.setEnabled(false);
        LinearLayout.LayoutParams codexParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        codexParams.leftMargin = dp(4);
        tabs.addView(codex, codexParams);
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        archiveContent = new LinearLayout(this);
        archiveContent.setOrientation(LinearLayout.VERTICAL);
        archiveContent.setPadding(0, dp(12), 0, dp(28));
        scroll.addView(archiveContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void renderArchive() {
        if (archiveContent == null || archiveStore == null) return;
        archiveContent.removeAllViews();
        List<NpcArchiveStore.Record> records = archiveStore.records();
        if (records.isEmpty()) {
            renderEmpty();
            return;
        }

        boolean selectedExists = false;
        for (NpcArchiveStore.Record record : records) {
            if (selectedNpcId.equals(record.npcId)) {
                selectedExists = true;
                break;
            }
        }
        if (!selectedExists) selectedNpcId = records.get(0).npcId;

        HorizontalScrollView selectorScroll = new HorizontalScrollView(this);
        selectorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setPadding(0, 0, dp(4), 0);
        for (int i = 0; i < records.size(); i++) {
            NpcArchiveStore.Record record = records.get(i);
            Button button = archiveSelector(record);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(132), dp(52));
            if (i > 0) params.leftMargin = dp(8);
            selector.addView(button, params);
        }
        selectorScroll.addView(selector, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        archiveContent.addView(selectorScroll);

        NpcArchiveStore.Record selected = archiveStore.load(selectedNpcId);
        if (selected != null) renderRecord(selected);
    }

    private void renderEmpty() {
        LinearLayout empty = card(CARD, BORDER, 18);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(42), dp(24), dp(42));

        TextView icon = text("◇", 34, ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);

        TextView title = text("ARCHIVE EMPTY", 12, ACCENT, true);
        title.setLetterSpacing(0.16f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = wrap();
        tp.topMargin = dp(12);
        empty.addView(title, tp);

        TextView body = text("死亡したNPCの記録はまだありません。", 14, MUTED, false);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = wrap();
        bp.topMargin = dp(8);
        empty.addView(body, bp);

        archiveContent.addView(empty, matchWithTop(dp(12)));
    }

    private void renderRecord(NpcArchiveStore.Record record) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(heroGradient());
        archiveContent.addView(hero, matchWithTop(dp(12)));

        TextView archiveId = text("ARCHIVE · " + record.npcId.toUpperCase(Locale.ROOT),
                10, Color.rgb(219, 193, 135), true);
        archiveId.setLetterSpacing(0.14f);
        hero.addView(archiveId);

        TextView name = text(record.displayName(), 28, Color.WHITE, true);
        LinearLayout.LayoutParams np = wrap();
        np.topMargin = dp(6);
        hero.addView(name, np);

        TextView state = text("MEMORIAL RECORD", 11, Color.rgb(190, 199, 211), true);
        state.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams sp = wrap();
        sp.topMargin = dp(2);
        hero.addView(state, sp);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mp = matchWithTop(dp(14));
        hero.addView(metrics, mp);
        metrics.addView(metricCard("到達階", record.floor + "F"),
                new LinearLayout.LayoutParams(0, dp(88), 1f));
        LinearLayout.LayoutParams turnParams = new LinearLayout.LayoutParams(0, dp(88), 1f);
        turnParams.leftMargin = dp(10);
        metrics.addView(metricCard("行動ターン", Integer.toString(record.turn)), turnParams);

        LinearLayout personality = card(CARD, BORDER, 18);
        personality.setPadding(dp(16), dp(15), dp(16), dp(16));
        archiveContent.addView(personality, matchWithTop(dp(12)));
        addSectionHeading(personality, "PERSONALITY", "性格");

        JSONObject traits = record.traits();
        addTraitRow(personality, "外向性", percent(traits.optDouble("extraversion", 0.0)));
        addTraitRow(personality, "神経症傾向", percent(traits.optDouble("neuroticism", 0.0)));
        addTraitRow(personality, "協調性", percent(traits.optDouble("agreeableness", 0.0)));
        addTraitRow(personality, "誠実性", percent(traits.optDouble("conscientiousness", 0.0)));
        addTraitRow(personality, "開放性", percent(traits.optDouble("openness", 0.0)));

        if (!record.speechStyle().isEmpty()) {
            TextView speech = text("話し方  " + record.speechStyle(), 12,
                    Color.rgb(188, 198, 211), false);
            LinearLayout.LayoutParams speechParams = wrap();
            speechParams.topMargin = dp(12);
            personality.addView(speech, speechParams);
        }

        LinearLayout mind = card(CARD, BORDER, 18);
        mind.setPadding(dp(16), dp(15), dp(16), dp(16));
        archiveContent.addView(mind, matchWithTop(dp(12)));
        addSectionHeading(mind, "MIND MAP", "マインドマップ");

        boolean validMindMap = CognitiveGraphBuilder.isValidSemanticSnapshot(record.mindMap);
        if (!validMindMap) {
            TextView unavailable = text("死亡時点で保存できる認知グラフはありませんでした。",
                    13, MUTED, false);
            LinearLayout.LayoutParams up = wrap();
            up.topMargin = dp(14);
            mind.addView(unavailable, up);
            return;
        }

        TextView hint = text("ドラッグで回転 · ピンチで拡大 · 点をタップで詳細",
                10, MUTED, false);
        LinearLayout.LayoutParams hp = wrap();
        hp.topMargin = dp(5);
        mind.addView(hint, hp);

        CognitiveSphereView sphere = new CognitiveSphereView(this);
        sphere.setContentDescription("死亡時に保存された認知グラフ");
        sphere.setGraph(CognitiveGraphBuilder.buildFromSemanticSnapshot(record.mindMap));
        sphere.setNodeListener(this::showNodeDetails);
        LinearLayout.LayoutParams sphereParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(330));
        sphereParams.topMargin = dp(10);
        mind.addView(sphere, sphereParams);

        Button reset = new Button(this);
        reset.setText("視点リセット");
        reset.setAllCaps(false);
        reset.setTextSize(11);
        reset.setTextColor(TEXT);
        reset.setBackground(cardBackground(CARD_ALT, BORDER, 12));
        reset.setOnClickListener(v -> sphere.resetView());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        resetParams.topMargin = dp(8);
        mind.addView(reset, resetParams);
    }

    private Button archiveSelector(NpcArchiveStore.Record record) {
        boolean selected = record.npcId.equals(selectedNpcId);
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(record.displayName());
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? Color.rgb(20, 17, 12) : TEXT);
        button.setBackground(cardBackground(
                selected ? ACCENT : CARD_ALT,
                selected ? Color.rgb(235, 207, 148) : BORDER,
                14));
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> {
            selectedNpcId = record.npcId;
            renderArchive();
        });
        return button;
    }

    private LinearLayout metricCard(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(9), dp(13), dp(9));
        card.setBackground(cardBackground(Color.argb(115, 16, 20, 27),
                Color.argb(130, 226, 194, 128), 14));
        TextView key = text(label, 10, Color.rgb(188, 195, 205), true);
        card.addView(key);
        TextView val = text(value, 25, Color.WHITE, true);
        LinearLayout.LayoutParams vp = wrap();
        vp.topMargin = dp(2);
        card.addView(val, vp);
        return card;
    }

    private void addSectionHeading(LinearLayout parent, String english, String japanese) {
        TextView e = text(english, 10, ACCENT, true);
        e.setLetterSpacing(0.15f);
        parent.addView(e);
        TextView j = text(japanese, 18, TEXT, true);
        LinearLayout.LayoutParams jp = wrap();
        jp.topMargin = dp(2);
        parent.addView(j, jp);
    }

    private void addTraitRow(LinearLayout parent, String label, int value) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topParams = matchWithTop(dp(12));
        parent.addView(top, topParams);

        TextView key = text(label, 12, Color.rgb(199, 208, 220), true);
        top.addView(key, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView number = text(value + "%", 12, TEXT, true);
        top.addView(number);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(cardBackground(Color.rgb(34, 43, 55), Color.rgb(34, 43, 55), 5));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(7));
        trackParams.topMargin = dp(5);
        parent.addView(track, trackParams);

        View fill = new View(this);
        fill.setBackground(cardBackground(ACCENT, ACCENT, 5));
        track.addView(fill, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, value)));
        View rest = new View(this);
        track.addView(rest, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, 100 - value)));
    }

    private void showNodeDetails(CognitiveGraph.Node node) {
        StringBuilder body = new StringBuilder();
        body.append(node.detail.isEmpty() ? "公開要約なし" : node.detail);
        body.append("\n\n種類: ").append(node.type);
        if (!node.moduleId.isEmpty()) body.append("\n領域: ").append(node.moduleId);
        body.append("\nactivation: ")
                .append((int) Math.round(node.activation * 100.0)).append("%");
        body.append("\n信頼度: ")
                .append((int) Math.round(node.confidence * 100.0)).append("%");
        new AlertDialog.Builder(this)
                .setTitle(node.label.isEmpty() ? "認知ポイント" : node.label)
                .setMessage(body.toString())
                .setPositiveButton("閉じる", null)
                .show();
    }

    private int percent(double value) {
        return Math.max(0, Math.min(100, (int) Math.round(value * 100.0)));
    }

    private LinearLayout card(int fill, int stroke, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(fill, stroke, radiusDp));
        return card;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button tabButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? Color.rgb(24, 19, 11) : Color.rgb(190, 201, 216));
        button.setBackground(cardBackground(
                selected ? ACCENT : Color.rgb(17, 26, 39),
                selected ? Color.rgb(231, 203, 144) : Color.rgb(41, 55, 73), 12));
        button.setMinHeight(dp(48));
        return button;
    }

    private void openConversation() {
        Intent intent = new Intent(this, DemoActivityV032.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openNpcStatus() {
        Intent intent = new Intent(this, NpcStatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openDungeon() {
        Intent intent = new Intent(this, DungeonActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private GradientDrawable backgroundGradient() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{BG_TOP, BG_BOTTOM});
    }

    private GradientDrawable heroGradient() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(52, 43, 27), Color.rgb(20, 28, 40), Color.rgb(13, 20, 31)});
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), Color.rgb(108, 88, 52));
        return drawable;
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWithTop(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(BG_TOP);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    }

    private void applySafeInsets(View root) {
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
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
                view.setPadding(dp(14) + left, dp(8) + top,
                        dp(14) + right, dp(8) + bottom);
                return insets;
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
