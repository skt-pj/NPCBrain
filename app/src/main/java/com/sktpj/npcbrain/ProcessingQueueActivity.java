package com.sktpj.npcbrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Debug-only queue monitor. The top level is always one card per NPC brain. */
public final class ProcessingQueueActivity extends Activity {
    private static final long REFRESH_MS = 500L;
    private static final int MAX_RECENT_DETAIL_ROWS = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> expandedNpcIds = new HashSet<>();
    private LinearLayout list;
    private boolean resumed;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (!resumed || isFinishing()) return;
            render();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!NPCBrainApplication.isDebugBuild()) {
            finish();
            return;
        }
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!NPCBrainApplication.isDebugBuild()) return;
        resumed = true;
        handler.removeCallbacks(refreshTask);
        refreshTask.run();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUiTheme.APP_BACKGROUND);
        root.setPadding(dp(18), dp(18), dp(18), dp(12));

        TextView title = new TextView(this);
        title.setText("NPC脳キュー · DEBUG");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AppUiTheme.APP_TEXT);
        root.addView(title);

        TextView description = new TextView(this);
        description.setText("NPCごとに脳処理を1件へ集約します。NPCをタップすると、9専門Brain・Global Workspace・その他の内部処理と、各処理のキュー投入・デキュー・待機時間・実処理時間を表示します。");
        description.setTextSize(12f);
        description.setTextColor(AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(8);
        descriptionParams.bottomMargin = dp(14);
        root.addView(description, descriptionParams);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(18));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return root;
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();

        List<NpcBrainQueueViewModel.BrainGroup> groups = NpcBrainQueueViewModel.group(
                ProcessingQueueRegistry.snapshot());
        addSectionTitle("NPCごとの脳処理");
        if (groups.isEmpty()) {
            addEmpty("表示できるNPC脳処理はまだありません");
            return;
        }

        for (NpcBrainQueueViewModel.BrainGroup group : groups) {
            list.addView(brainCard(group));
        }
    }

    private View brainCard(NpcBrainQueueViewModel.BrainGroup group) {
        LinearLayout card = baseCard();
        boolean expanded = expandedNpcIds.contains(group.npcId);

        TextView headline = new TextView(this);
        headline.setText(group.npcId + " · " + brainStateLabel(group));
        headline.setTextSize(15f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(headlineColor(group));
        card.addView(headline);

        TextView summary = new TextView(this);
        summary.setText(brainSummary(group));
        summary.setTextSize(11f);
        summary.setTextColor(AppUiTheme.APP_MUTED);
        summary.setLineSpacing(0f, 1.18f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(5);
        card.addView(summary, summaryParams);

        TextView toggle = new TextView(this);
        toggle.setText(expanded ? "▼ 詳細を閉じる" : "▶ タップして内部状況を見る");
        toggle.setTextSize(11f);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setTextColor(AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.topMargin = dp(7);
        card.addView(toggle, toggleParams);

        if (expanded) {
            addExpandedDetails(card, group);
        }

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (expandedNpcIds.contains(group.npcId)) {
                expandedNpcIds.remove(group.npcId);
            } else {
                expandedNpcIds.add(group.npcId);
            }
            render();
        });
        return card;
    }

    private String brainStateLabel(NpcBrainQueueViewModel.BrainGroup group) {
        ProcessingQueueRegistry.Status status = group.headlineStatus();
        if (group.hasActive()) {
            return status == ProcessingQueueRegistry.Status.RUNNING ? "脳処理中" : "脳待機中";
        }
        return status == ProcessingQueueRegistry.Status.FAILED ? "直近で失敗" : "直近完了";
    }

    private int headlineColor(NpcBrainQueueViewModel.BrainGroup group) {
        if (!group.hasActive() && group.headlineStatus() == ProcessingQueueRegistry.Status.FAILED) {
            return Color.rgb(224, 116, 116);
        }
        return AppUiTheme.APP_TEXT;
    }

    private String brainSummary(NpcBrainQueueViewModel.BrainGroup group) {
        if (group.hasActive()) {
            long earliest = earliestQueued(group.active);
            int running = group.runningCount();
            int queued = group.queuedCount();
            int llm = group.activeLlmCount();
            StringBuilder result = new StringBuilder();
            result.append("最初のキュー投入 ").append(formatClock(earliest));
            result.append("\n内部処理  実行中 ").append(running)
                    .append("件 · 待機中 ").append(queued).append("件");
            if (llm > 0) result.append(" · LLM ").append(llm).append("件");
            return result.toString();
        }
        long latest = latestFinished(group.recent);
        return "最終更新 " + formatClock(latest)
                + "\n直近の内部結果 " + group.recent.size() + "件";
    }

    private void addExpandedDetails(
            LinearLayout card,
            NpcBrainQueueViewModel.BrainGroup group
    ) {
        TextView divider = new TextView(this);
        divider.setText("内部状況");
        divider.setTextSize(12f);
        divider.setTypeface(Typeface.DEFAULT_BOLD);
        divider.setTextColor(AppUiTheme.APP_TEXT);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dividerParams.topMargin = dp(12);
        dividerParams.bottomMargin = dp(4);
        card.addView(divider, dividerParams);

        if (group.active.isEmpty()) {
            addInlineEmpty(card, "現在実行中・待機中の内部処理はありません");
        } else {
            int queuedPosition = 0;
            for (ProcessingQueueRegistry.Entry entry : group.active) {
                if (entry.status == ProcessingQueueRegistry.Status.QUEUED) queuedPosition++;
                card.addView(internalRow(entry, queuedPosition));
            }
        }

        if (!group.recent.isEmpty()) {
            TextView recentTitle = new TextView(this);
            recentTitle.setText("直近の内部結果");
            recentTitle.setTextSize(12f);
            recentTitle.setTypeface(Typeface.DEFAULT_BOLD);
            recentTitle.setTextColor(AppUiTheme.APP_TEXT);
            LinearLayout.LayoutParams recentTitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            recentTitleParams.topMargin = dp(10);
            recentTitleParams.bottomMargin = dp(4);
            card.addView(recentTitle, recentTitleParams);

            int count = 0;
            for (ProcessingQueueRegistry.Entry entry : group.recent) {
                card.addView(internalRow(entry, 0));
                count++;
                if (count >= MAX_RECENT_DETAIL_ROWS) break;
            }
        }
    }

    private View internalRow(ProcessingQueueRegistry.Entry entry, int queuedPosition) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(innerBackground());
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(6);
        row.setLayoutParams(rowParams);

        TextView label = new TextView(this);
        label.setText(internalStatusLabel(entry.status, queuedPosition)
                + "  " + NpcBrainQueueViewModel.internalLabel(entry));
        label.setTextSize(12f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(entry.status == ProcessingQueueRegistry.Status.FAILED
                ? Color.rgb(224, 116, 116) : AppUiTheme.APP_TEXT);
        row.addView(label);

        String model = NpcBrainQueueViewModel.modelLabel(entry);
        if (!model.isEmpty()) {
            TextView modelView = new TextView(this);
            modelView.setText(model);
            modelView.setTextSize(10f);
            modelView.setTextColor(AppUiTheme.APP_MUTED);
            LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            modelParams.topMargin = dp(2);
            row.addView(modelView, modelParams);
        }

        TextView times = new TextView(this);
        times.setText(queueTimeText(entry));
        times.setTextSize(10f);
        times.setTextColor(AppUiTheme.APP_MUTED);
        times.setLineSpacing(0f, 1.14f);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        row.addView(times, timeParams);

        if (entry.status == ProcessingQueueRegistry.Status.FAILED && !entry.error.isEmpty()) {
            TextView error = new TextView(this);
            error.setText(entry.error);
            error.setTextSize(10f);
            error.setTextColor(Color.rgb(224, 116, 116));
            LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            errorParams.topMargin = dp(4);
            row.addView(error, errorParams);
        }
        return row;
    }

    private String internalStatusLabel(ProcessingQueueRegistry.Status status, int queuedPosition) {
        if (status == ProcessingQueueRegistry.Status.QUEUED) {
            return "待機中" + (queuedPosition > 0 ? " #" + queuedPosition : "");
        }
        if (status == ProcessingQueueRegistry.Status.RUNNING) return "実行中";
        if (status == ProcessingQueueRegistry.Status.COMPLETED) return "完了";
        return "失敗";
    }

    private String queueTimeText(ProcessingQueueRegistry.Entry entry) {
        long now = System.currentTimeMillis();
        StringBuilder text = new StringBuilder();
        text.append("キュー投入  ").append(formatClock(entry.queuedAtMs));
        text.append("\nデキュー    ").append(entry.hasBeenDequeued()
                ? formatClock(entry.dequeuedAtMs()) : "--:--:--");
        text.append("\n待機時間    ").append(formatDuration(entry.queueWaitDurationMs(now)));
        if (entry.hasBeenDequeued()) {
            text.append("\n実処理時間  ").append(formatDuration(entry.processingDurationMs(now)));
            if (entry.status == ProcessingQueueRegistry.Status.RUNNING) {
                text.append("（計測中）");
            }
        } else {
            text.append("\n実処理時間  --");
        }
        return text.toString();
    }

    private long earliestQueued(List<ProcessingQueueRegistry.Entry> entries) {
        long result = Long.MAX_VALUE;
        for (ProcessingQueueRegistry.Entry entry : entries) {
            result = Math.min(result, entry.queuedAtMs);
        }
        return result == Long.MAX_VALUE ? 0L : result;
    }

    private long latestFinished(List<ProcessingQueueRegistry.Entry> entries) {
        long result = 0L;
        for (ProcessingQueueRegistry.Entry entry : entries) {
            result = Math.max(result, Math.max(entry.finishedAtMs, entry.queuedAtMs));
        }
        return result;
    }

    private void addSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AppUiTheme.APP_TEXT);
        title.setPadding(dp(2), dp(10), dp(2), dp(6));
        list.addView(title);
    }

    private void addEmpty(String text) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextSize(13f);
        empty.setTextColor(AppUiTheme.APP_MUTED);
        empty.setPadding(dp(4), dp(12), dp(4), dp(18));
        list.addView(empty);
    }

    private void addInlineEmpty(LinearLayout card, String text) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextSize(10f);
        empty.setTextColor(AppUiTheme.APP_MUTED);
        empty.setPadding(dp(2), dp(5), dp(2), dp(5));
        card.addView(empty);
    }

    private LinearLayout baseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(9);
        card.setLayoutParams(cardParams);
        return card;
    }

    private String formatClock(long millis) {
        if (millis <= 0L) return "--:--:--";
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN).format(new Date(millis));
    }

    private String formatDuration(long millis) {
        if (millis < 1000L) return millis + "ms";
        double seconds = millis / 1000.0;
        if (seconds < 60.0) return String.format(Locale.JAPAN, "%.1f秒", seconds);
        long totalSeconds = millis / 1000L;
        return String.format(Locale.JAPAN, "%d分%02d秒", totalSeconds / 60L, totalSeconds % 60L);
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(AppUiTheme.APP_SURFACE);
        drawable.setStroke(dp(1), AppUiTheme.NAV_DIVIDER);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private GradientDrawable innerBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(AppUiTheme.APP_BACKGROUND);
        drawable.setStroke(dp(1), AppUiTheme.NAV_DIVIDER);
        drawable.setCornerRadius(dp(9));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
