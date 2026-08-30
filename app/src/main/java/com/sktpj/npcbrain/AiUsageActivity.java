package com.sktpj.npcbrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only current-window AI usage summary for all active NPCs. */
public final class AiUsageActivity extends Activity {
    private LinearLayout content;
    private NpcRegistryStore registryStore;
    private NpcAiStaminaStore staminaStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registryStore = new NpcRegistryStore(this);
        staminaStore = new NpcAiStaminaStore(this);
        setContentView(buildView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private ScrollView buildView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(7, 12, 22));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("全員のAI使用量", 21, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button back = new Button(this);
        back.setAllCaps(false);
        back.setText("戻る");
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.rgb(25, 50, 78));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(88), dp(46)));
        content.addView(header);

        TextView note = text("現在の予算枠 + リセットされない生涯累計 · Responses API usage の概算", 11,
                Color.rgb(132, 157, 190), false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(4);
        content.addView(note, noteParams);

        List<String> npcIds = registryStore.activeNpcIds();
        List<NpcAiStaminaStore.Snapshot> snapshots = new ArrayList<>();
        for (String npcId : npcIds) snapshots.add(staminaStore.snapshot(npcId));
        NpcAiUsageDisplayPolicy.Aggregate aggregate =
                NpcAiUsageDisplayPolicy.aggregate(snapshots);

        TextView total = text(totalText(aggregate), 14, Color.rgb(230, 238, 249), true);
        total.setPadding(dp(14), dp(14), dp(14), dp(14));
        total.setBackgroundColor(Color.rgb(16, 26, 40));
        LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        totalParams.topMargin = dp(14);
        content.addView(total, totalParams);

        TextView breakdownTitle = text("NPC別内訳", 15, Color.rgb(218, 232, 250), true);
        LinearLayout.LayoutParams breakdownParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        breakdownParams.topMargin = dp(18);
        content.addView(breakdownTitle, breakdownParams);

        if (npcIds.isEmpty()) {
            TextView empty = text("active NPCはいません。", 13,
                    Color.rgb(160, 176, 196), false);
            content.addView(empty);
            return;
        }

        for (int i = 0; i < npcIds.size(); i++) {
            String npcId = npcIds.get(i);
            NpcAiStaminaStore.Snapshot snapshot = snapshots.get(i);
            CharacterStateStore character =
                    new CharacterStateStore(NpcContexts.storage(this, npcId));
            String name = character.displayName();
            String label = (name == null || name.trim().isEmpty() || "NPC".equals(name.trim()))
                    ? npcId.toUpperCase(Locale.US)
                    : name.trim() + "  (" + npcId + ")";
            TextView row = text(
                    label + "\n"
                            + "現在枠 " + NpcAiUsageDisplayPolicy.formatSpentJpy(snapshot.spentJpy)
                            + " / 上限 " + NpcAiUsageDisplayPolicy.formatRemainingJpy(snapshot.budgetLimitJpy)
                            + " · 残額 " + NpcAiUsageDisplayPolicy.formatRemainingJpy(snapshot.remainingJpy)
                            + "\n累計 " + NpcAiUsageDisplayPolicy.formatSpentJpy(snapshot.lifetimeSpentJpy)
                            + " · total token " + String.format(Locale.JAPAN, "%,d", snapshot.lifetimeTotalTokens)
                            + "\n現在枠 token: input " + String.format(Locale.JAPAN, "%,d", snapshot.inputTokens)
                            + " · cached " + String.format(Locale.JAPAN, "%,d", snapshot.cachedInputTokens)
                            + " · output " + String.format(Locale.JAPAN, "%,d", snapshot.outputTokens)
                            + " · total " + String.format(Locale.JAPAN, "%,d", snapshot.totalTokens),
                    12,
                    Color.rgb(225, 235, 248),
                    false);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundColor(Color.rgb(14, 23, 36));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(8);
            content.addView(row, rowParams);
        }
    }

    private static String totalText(NpcAiUsageDisplayPolicy.Aggregate aggregate) {
        return "現在枠の全NPC合計  " + aggregate.npcCount + "人\n"
                + "消費 " + NpcAiUsageDisplayPolicy.formatSpentJpy(aggregate.spentJpy)
                + " / 総予算 " + String.format(Locale.JAPAN, "¥%.2f", aggregate.budgetJpy)
                + "\n残額 " + NpcAiUsageDisplayPolicy.formatRemainingJpy(aggregate.remainingJpy)
                + "\ninput " + String.format(Locale.JAPAN, "%,d", aggregate.inputTokens)
                + " · cached " + String.format(Locale.JAPAN, "%,d", aggregate.cachedInputTokens)
                + "\noutput " + String.format(Locale.JAPAN, "%,d", aggregate.outputTokens)
                + " · total " + String.format(Locale.JAPAN, "%,d", aggregate.totalTokens);
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
