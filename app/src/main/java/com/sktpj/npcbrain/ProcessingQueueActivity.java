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
import java.util.Locale;

/** Debug-only read-only view of user-meaningful processing queue items. */
public final class ProcessingQueueActivity extends Activity {
    private static final long REFRESH_MS = 500L;
    private static final int MAX_RECENT_VISIBLE = 20;

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
        description.setText("会話返信・自発送信判断・記憶整理など、実際の処理単位だけを表示します。Brain内部のLLM呼び出しは表示しません。");
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

        addSectionTitle("現在の処理");
        int waitingPosition = 0;
        boolean hasActive = false;
        for (ProcessingQueueRegistry.Entry entry : snapshot.active) {
            if (!isVisibleProcess(entry)) continue;
            if (entry.status == ProcessingQueueRegistry.Status.QUEUED) waitingPosition++;
            list.addView(processCard(entry, waitingPosition));
            hasActive = true;
        }
        if (!hasActive) {
            addEmpty("現在、待機中・実処理中の処理はありません");
        }

        addSectionTitle("直近の処理結果");
        int recentCount = 0;
        for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
            if (!isVisibleProcess(entry)) continue;
            list.addView(processCard(entry, 0));
            recentCount++;
            if (recentCount >= MAX_RECENT_VISIBLE) break;
        }
        if (recentCount == 0) {
            addEmpty("完了・失敗した処理はまだありません");
        }
    }

    /**
     * LLM invocation rows are implementation detail. The real LiteRT FIFO remains active, but
     * individual specialist/OpenAI calls must never appear as user-facing queue items.
     */
    static boolean isVisibleProcess(ProcessingQueueRegistry.Entry entry) {
        return entry != null && !"llm_request".equals(entry.type);
    }

    private View processCard(ProcessingQueueRegistry.Entry entry, int waitingPosition) {
        LinearLayout card = baseCard();

        TextView headline = new TextView(this);
        String npc = entry.npcId.isEmpty() ? "" : " · " + entry.npcId;
        headline.setText(statusLabel(entry.status, waitingPosition) + "  "
                + ProcessingQueueRegistry.displayType(entry.type) + npc);
        headline.setTextSize(14f);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(AppUiTheme.APP_TEXT);
        card.addView(headline);

        TextView times = new TextView(this);
        times.setText(queueTimeText(entry));
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

    private String statusLabel(ProcessingQueueRegistry.Status status, int waitingPosition) {
        if (status == ProcessingQueueRegistry.Status.QUEUED) {
            return "待機中" + (waitingPosition > 0 ? " #" + waitingPosition : "");
        }
        if (status == ProcessingQueueRegistry.Status.RUNNING) return "実処理中";
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

    private String cleanDetail(String detail) {
        if (detail == null) return "";
        String value = detail.trim();
        if (value.startsWith("room=")) return "room: " + value.substring("room=".length());
        if ("foreground spontaneous".equals(value)) return "フォアグラウンド自発判断";
        return value;
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
