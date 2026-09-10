package com.sktpj.npcbrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        description.setText("現在実行中・待機中の処理と直近の結果を表示します。表示は処理順や並列実行を変更しません。");
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

        addSectionTitle("現在");
        if (snapshot.active.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("現在の処理はありません");
            empty.setTextSize(13f);
            empty.setTextColor(AppUiTheme.APP_MUTED);
            empty.setPadding(dp(4), dp(12), dp(4), dp(18));
            list.addView(empty);
        } else {
            for (ProcessingQueueRegistry.Entry entry : snapshot.active) {
                list.addView(entryCard(entry, true));
            }
        }

        addSectionTitle("直近");
        if (snapshot.recent.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("完了した処理はまだありません");
            empty.setTextSize(13f);
            empty.setTextColor(AppUiTheme.APP_MUTED);
            empty.setPadding(dp(4), dp(12), dp(4), dp(18));
            list.addView(empty);
        } else {
            for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
                list.addView(entryCard(entry, false));
            }
        }
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

    private View entryCard(ProcessingQueueRegistry.Entry entry, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground());

        TextView headline = new TextView(this);
        String npc = entry.npcId.isEmpty() ? "" : " · " + entry.npcId;
        headline.setText(statusLabel(entry.status) + "  "
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
        return value;
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
}
