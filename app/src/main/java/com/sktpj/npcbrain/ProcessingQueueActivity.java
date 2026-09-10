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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Debug-only read-only view of the process-local diagnostic queue. */
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
        description.setText("上段は論理処理の待機・実行順です。Brain内部の並列LLM処理はキュー項目ではないため別枠にまとめます。");
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

        List<ProcessingQueueRegistry.Entry> queueActive = new ArrayList<>();
        List<ProcessingQueueRegistry.Entry> internalActive = new ArrayList<>();
        for (ProcessingQueueRegistry.Entry entry : snapshot.active) {
            if (isInternalWorker(entry)) internalActive.add(entry);
            else queueActive.add(entry);
        }

        addSectionTitle("処理キュー");
        if (queueActive.isEmpty()) {
            addEmpty("待機中・実行中の論理処理はありません");
        } else {
            int waitingPosition = 0;
            for (ProcessingQueueRegistry.Entry entry : queueActive) {
                if (entry.status == ProcessingQueueRegistry.Status.QUEUED) waitingPosition++;
                list.addView(entryCard(entry, true, waitingPosition));
            }
        }

        if (!internalActive.isEmpty()) {
            addSectionTitle("内部並列処理");
            addHint("これはキューの複数同時実行ではありません。現在の論理処理の内部で並列実行されているワーカーです。");
            for (WorkerGroup group : groupWorkers(internalActive)) {
                list.addView(workerGroupCard(group));
            }
        }

        addSectionTitle("直近の論理処理");
        boolean hasRecentLogical = false;
        for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
            if (isInternalWorker(entry)) continue;
            list.addView(entryCard(entry, false, 0));
            hasRecentLogical = true;
        }
        if (!hasRecentLogical) {
            addEmpty("完了した論理処理はまだありません");
        }

        boolean hasInternalFailure = false;
        for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
            if (!isInternalWorker(entry)
                    || entry.status != ProcessingQueueRegistry.Status.FAILED) continue;
            if (!hasInternalFailure) {
                addSectionTitle("内部処理の失敗");
                hasInternalFailure = true;
            }
            list.addView(entryCard(entry, false, 0));
        }
    }

    static boolean isInternalWorker(ProcessingQueueRegistry.Entry entry) {
        return entry != null && "llm_request".equals(entry.type);
    }

    private List<WorkerGroup> groupWorkers(List<ProcessingQueueRegistry.Entry> entries) {
        Map<String, WorkerGroup> groups = new LinkedHashMap<>();
        for (ProcessingQueueRegistry.Entry entry : entries) {
            String key = entry.npcId + "\u0000" + entry.detail;
            WorkerGroup group = groups.get(key);
            if (group == null) {
                group = new WorkerGroup(entry.npcId, entry.detail);
                groups.put(key, group);
            }
            group.add(entry);
        }
        return new ArrayList<>(groups.values());
    }

    private View workerGroupCard(WorkerGroup group) {
        LinearLayout card = baseCard();

        TextView headline = new TextView(this);
        String npc = group.npcId.isEmpty() ? "" : " · " + group.npcId;
        headline.setText("内部並列  LLM推論" + npc + "  × " + group.count + "件");
        headline.setTextSize(14f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(AppUiTheme.APP_TEXT);
        card.addView(headline);

        TextView time = new TextView(this);
        long now = System.currentTimeMillis();
        time.setText(group.count + "件実行中 · 最古開始 " + formatClock(group.earliestStartMs)
                + " · " + formatDuration(Math.max(0L, now - group.earliestStartMs)));
        time.setTextSize(11f);
        time.setTextColor(AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        card.addView(time, timeParams);

        if (!group.detail.isEmpty()) {
            TextView detail = new TextView(this);
            detail.setText(cleanWorkerDetail(group.detail));
            detail.setTextSize(11f);
            detail.setTextColor(AppUiTheme.APP_MUTED);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(5);
            card.addView(detail, detailParams);
        }
        return card;
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

    private View entryCard(ProcessingQueueRegistry.Entry entry, boolean active, int waitingPosition) {
        LinearLayout card = baseCard();

        TextView headline = new TextView(this);
        String npc = entry.npcId.isEmpty() ? "" : " · " + entry.npcId;
        String queuePosition = entry.status == ProcessingQueueRegistry.Status.QUEUED && waitingPosition > 0
                ? " #" + waitingPosition : "";
        headline.setText(statusLabel(entry.status) + queuePosition + "  "
                + ProcessingQueueRegistry.displayType(entry.type) + npc);
        headline.setTextSize(14f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(AppUiTheme.APP_TEXT);
        card.addView(headline);

        TextView time = new TextView(this);
        time.setText(timeText(entry, active));
        time.setTextSize(11f);
        time.setTextColor(AppUiTheme.APP_MUTED);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(4);
        card.addView(time, timeParams);

        String detail = entry.status == ProcessingQueueRegistry.Status.FAILED
                ? entry.error : cleanDetail(entry.detail);
        if (!detail.isEmpty()) {
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
        return card;
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

    private String timeText(ProcessingQueueRegistry.Entry entry, boolean active) {
        long now = System.currentTimeMillis();
        if (entry.status == ProcessingQueueRegistry.Status.QUEUED) {
            return "待機開始 " + formatClock(entry.queuedAtMs)
                    + " · " + formatDuration(Math.max(0L, now - entry.queuedAtMs));
        }
        if (entry.status == ProcessingQueueRegistry.Status.RUNNING) {
            long start = entry.startedAtMs > 0L ? entry.startedAtMs : entry.queuedAtMs;
            return "開始 " + formatClock(start)
                    + " · " + formatDuration(Math.max(0L, now - start));
        }
        long start = entry.startedAtMs > 0L ? entry.startedAtMs : entry.queuedAtMs;
        long finish = entry.finishedAtMs > 0L ? entry.finishedAtMs : start;
        return "開始 " + formatClock(start)
                + " · 終了 " + formatClock(finish)
                + " · " + formatDuration(Math.max(0L, finish - start));
    }

    private String statusLabel(ProcessingQueueRegistry.Status status) {
        if (status == ProcessingQueueRegistry.Status.RUNNING) return "実行中";
        if (status == ProcessingQueueRegistry.Status.COMPLETED) return "完了";
        if (status == ProcessingQueueRegistry.Status.FAILED) return "失敗";
        return "待機中";
    }

    private String cleanDetail(String detail) {
        if (detail == null) return "";
        String value = detail.trim();
        if (value.startsWith("room=")) return "room: " + value.substring("room=".length());
        if ("foreground spontaneous".equals(value)) return "フォアグラウンド自発判断";
        return value;
    }

    private String cleanWorkerDetail(String detail) {
        if (detail == null) return "";
        return detail.trim().replace("specialist / task", "専門領域 / task");
    }

    private String formatClock(long millis) {
        if (millis <= 0L) return "--:--:--";
        return new SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(new Date(millis));
    }

    private String formatDuration(long millis) {
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

    private static final class WorkerGroup {
        final String npcId;
        final String detail;
        int count;
        long earliestStartMs = Long.MAX_VALUE;

        WorkerGroup(String npcId, String detail) {
            this.npcId = npcId == null ? "" : npcId;
            this.detail = detail == null ? "" : detail;
        }

        void add(ProcessingQueueRegistry.Entry entry) {
            count++;
            long start = entry.startedAtMs > 0L ? entry.startedAtMs : entry.queuedAtMs;
            earliestStartMs = Math.min(earliestStartMs, start);
        }
    }
}
