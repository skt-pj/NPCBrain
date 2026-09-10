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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Debug-only read-only view of logical orchestration and the real local-LLM FIFO queue. */
public final class ProcessingQueueActivity extends Activity {
    private static final long REFRESH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
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
        title.setText("処理キュー · DEBUG");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AppUiTheme.APP_TEXT);
        root.addView(title);

        TextView description = new TextView(this);
        description.setText("ローカルLLMはFIFOで1件ずつ実行します。各項目には、キュー投入・デキュー・待機時間・実処理時間を実測値で表示します。");
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
        ProcessingQueueRegistry.Snapshot snapshot = ProcessingQueueRegistry.snapshot();

        List<ProcessingQueueRegistry.Entry> parentProcesses = new ArrayList<>();
        List<ProcessingQueueRegistry.Entry> localLlm = new ArrayList<>();
        List<ProcessingQueueRegistry.Entry> openAi = new ArrayList<>();
        for (ProcessingQueueRegistry.Entry entry : snapshot.active) {
            if (isLocalLlm(entry)) localLlm.add(entry);
            else if (isLlm(entry)) openAi.add(entry);
            else parentProcesses.add(entry);
        }

        addSectionTitle("親処理");
        addHint("会話返信や自発判断など、複数の子処理をまとめるオーケストレーションです。LLM実行スロットの『実行中』とは別の概念です。");
        if (parentProcesses.isEmpty()) {
            addEmpty("現在の親処理はありません");
        } else {
            for (ProcessingQueueRegistry.Entry entry : parentProcesses) {
                list.addView(parentProcessCard(entry));
            }
        }

        addSectionTitle("ローカルLLM FIFOキュー");
        int runningLocal = 0;
        for (ProcessingQueueRegistry.Entry entry : localLlm) {
            if (entry.status == ProcessingQueueRegistry.Status.RUNNING) runningLocal++;
        }
        if (runningLocal > 1) {
            addWarning("異常: ローカルLLMが " + runningLocal + " 件同時に実処理中です");
        } else {
            addHint("実行枠 1件 · 実処理中 " + runningLocal + "件 · 待機中 "
                    + Math.max(0, localLlm.size() - runningLocal) + "件");
        }
        if (localLlm.isEmpty()) {
            addEmpty("ローカルLLMの待機・実処理はありません");
        } else {
            int waitingPosition = 0;
            for (ProcessingQueueRegistry.Entry entry : localLlm) {
                if (entry.status == ProcessingQueueRegistry.Status.QUEUED) waitingPosition++;
                list.addView(localLlmCard(entry, waitingPosition));
            }
        }

        if (!openAi.isEmpty()) {
            addSectionTitle("OpenAI API処理");
            addHint("OpenAI Lunaは端末内LiteRT-LM FIFOの対象外です。");
            for (ProcessingQueueRegistry.Entry entry : openAi) {
                list.addView(genericEntryCard(entry));
            }
        }

        addSectionTitle("直近のローカルLLM結果");
        boolean hasRecentLocal = false;
        int recentLocalCount = 0;
        for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
            if (!isLocalLlm(entry)) continue;
            list.addView(localLlmCard(entry, 0));
            hasRecentLocal = true;
            recentLocalCount++;
            if (recentLocalCount >= 20) break;
        }
        if (!hasRecentLocal) {
            addEmpty("完了・失敗したローカルLLM処理はまだありません");
        }

        addSectionTitle("直近の親処理・その他結果");
        boolean hasRecentOther = false;
        int recentOtherCount = 0;
        for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
            if (isLocalLlm(entry)) continue;
            list.addView(isLlm(entry) ? genericEntryCard(entry) : parentProcessCard(entry));
            hasRecentOther = true;
            recentOtherCount++;
            if (recentOtherCount >= 20) break;
        }
        if (!hasRecentOther) {
            addEmpty("表示する結果はまだありません");
        }
    }

    static boolean isLlm(ProcessingQueueRegistry.Entry entry) {
        return entry != null && "llm_request".equals(entry.type);
    }

    static boolean isLocalLlm(ProcessingQueueRegistry.Entry entry) {
        return isLlm(entry) && entry.detail != null && entry.detail.startsWith("local_");
    }

    private View parentProcessCard(ProcessingQueueRegistry.Entry entry) {
        LinearLayout card = baseCard();

        TextView headline = new TextView(this);
        String npc = entry.npcId.isEmpty() ? "" : " · " + entry.npcId;
        String state = entry.status == ProcessingQueueRegistry.Status.QUEUED
                ? "親処理待機"
                : entry.status == ProcessingQueueRegistry.Status.COMPLETED
                ? "親処理完了"
                : entry.status == ProcessingQueueRegistry.Status.FAILED
                ? "親処理失敗"
                : "親処理中";
        headline.setText(state + "  " + ProcessingQueueRegistry.displayType(entry.type) + npc);
        headline.setTextSize(14f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(AppUiTheme.APP_TEXT);
        card.addView(headline);

        TextView time = new TextView(this);
        time.setText(parentTimeText(entry));
        time.setTextSize(11f);
        time.setTextColor(AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        card.addView(time, timeParams);

        addEntryDetail(card, entry);
        return card;
    }

    private View localLlmCard(ProcessingQueueRegistry.Entry entry, int waitingPosition) {
        LinearLayout card = baseCard();

        TextView headline = new TextView(this);
        String npc = entry.npcId.isEmpty() ? "" : " · " + entry.npcId;
        String state;
        if (entry.status == ProcessingQueueRegistry.Status.QUEUED) {
            state = "待機中" + (waitingPosition > 0 ? " #" + waitingPosition : "");
        } else if (entry.status == ProcessingQueueRegistry.Status.RUNNING) {
            state = "実処理中";
        } else if (entry.status == ProcessingQueueRegistry.Status.COMPLETED) {
            state = "完了";
        } else {
            state = "失敗";
        }
        headline.setText(state + "  LLM推論" + npc);
        headline.setTextSize(14f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(AppUiTheme.APP_TEXT);
        card.addView(headline);

        TextView times = new TextView(this);
        times.setText(localQueueTimeText(entry));
        times.setTextSize(11f);
        times.setTextColor(AppUiTheme.APP_MUTED);
        times.setLineSpacing(0f, 1.18f);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        card.addView(times, timeParams);

        addEntryDetail(card, entry);
        return card;
    }

    private View genericEntryCard(ProcessingQueueRegistry.Entry entry) {
        LinearLayout card = baseCard();

        TextView headline = new TextView(this);
        String npc = entry.npcId.isEmpty() ? "" : " · " + entry.npcId;
        headline.setText(genericStatusLabel(entry.status) + "  "
                + ProcessingQueueRegistry.displayType(entry.type) + npc);
        headline.setTextSize(14f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(AppUiTheme.APP_TEXT);
        card.addView(headline);

        TextView time = new TextView(this);
        time.setText(genericTimeText(entry));
        time.setTextSize(11f);
        time.setTextColor(AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        card.addView(time, timeParams);

        addEntryDetail(card, entry);
        return card;
    }

    private void addEntryDetail(LinearLayout card, ProcessingQueueRegistry.Entry entry) {
        String detail = entry.status == ProcessingQueueRegistry.Status.FAILED
                ? entry.error : cleanDetail(entry.detail);
        if (detail.isEmpty()) return;
        TextView details = new TextView(this);
        details.setText(detail);
        details.setTextSize(11f);
        details.setTextColor(entry.status == ProcessingQueueRegistry.Status.FAILED
                ? Color.rgb(224, 116, 116) : AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(5);
        card.addView(details, detailParams);
    }

    private String localQueueTimeText(ProcessingQueueRegistry.Entry entry) {
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

    private String parentTimeText(ProcessingQueueRegistry.Entry entry) {
        long now = System.currentTimeMillis();
        long end = entry.finishedAtMs > 0L ? entry.finishedAtMs : now;
        return "受付 " + formatClock(entry.queuedAtMs)
                + " · 経過 " + formatDuration(Math.max(0L, end - entry.queuedAtMs));
    }

    private String genericTimeText(ProcessingQueueRegistry.Entry entry) {
        long now = System.currentTimeMillis();
        long end = entry.finishedAtMs > 0L ? entry.finishedAtMs : now;
        return "受付 " + formatClock(entry.queuedAtMs)
                + " · 経過 " + formatDuration(Math.max(0L, end - entry.queuedAtMs));
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

    private void addHint(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextSize(11f);
        hint.setTextColor(AppUiTheme.APP_MUTED);
        hint.setPadding(dp(4), dp(2), dp(4), dp(8));
        list.addView(hint);
    }

    private void addWarning(String text) {
        TextView warning = new TextView(this);
        warning.setText(text);
        warning.setTextSize(12f);
        warning.setTypeface(Typeface.DEFAULT_BOLD);
        warning.setTextColor(Color.rgb(224, 116, 116));
        warning.setPadding(dp(4), dp(2), dp(4), dp(8));
        list.addView(warning);
    }

    private LinearLayout baseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);
        return card;
    }

    private String genericStatusLabel(ProcessingQueueRegistry.Status status) {
        if (status == ProcessingQueueRegistry.Status.RUNNING) return "処理中";
        if (status == ProcessingQueueRegistry.Status.COMPLETED) return "完了";
        if (status == ProcessingQueueRegistry.Status.FAILED) return "失敗";
        return "待機中";
    }

    private String cleanDetail(String detail) {
        if (detail == null) return "";
        String value = detail.trim();
        if (value.startsWith("room=")) return "room: " + value.substring("room=".length());
        if ("foreground spontaneous".equals(value)) return "フォアグラウンド自発判断";
        return value.replace("specialist / task", "専門領域 / task");
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
