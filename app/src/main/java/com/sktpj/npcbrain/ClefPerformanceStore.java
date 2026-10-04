package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Locale;

final class ClefPerformanceStore {
    private static final Object LOCK = new Object();
    private static final String PREFS = "npcbrain_clef_performance_v1";
    private static final String COUNT = "count";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String TOTAL_DURATION_MS = "total_duration_ms";
    private static final String LAST_DURATION_MS = "last_duration_ms";
    private static final String MAX_DURATION_MS = "max_duration_ms";
    private static final String LAST_PSS_BEFORE_KB = "last_pss_before_kb";
    private static final String LAST_PSS_AFTER_KB = "last_pss_after_kb";
    private static final String PEAK_PSS_KB = "peak_pss_kb";
    private static final String LAST_HEAP_BEFORE_BYTES = "last_heap_before_bytes";
    private static final String LAST_HEAP_AFTER_BYTES = "last_heap_after_bytes";
    private static final String PEAK_HEAP_BYTES = "peak_heap_bytes";
    private static final String LAST_AT_MS = "last_at_ms";
    private static final String LAST_MODEL = "last_model";
    private static final String LAST_BACKEND = "last_backend";
    private static final String LAST_SUCCESS = "last_success";
    private static final String LAST_ERROR = "last_error";

    static final class Snapshot {
        final long count;
        final long success;
        final long failure;
        final long totalDurationMs;
        final long lastDurationMs;
        final long maxDurationMs;
        final long lastPssBeforeKb;
        final long lastPssAfterKb;
        final long peakPssKb;
        final long lastHeapBeforeBytes;
        final long lastHeapAfterBytes;
        final long peakHeapBytes;
        final long lastAtMs;
        final String lastModel;
        final String lastBackend;
        final boolean lastSuccess;
        final String lastError;

        Snapshot(
                long count,
                long success,
                long failure,
                long totalDurationMs,
                long lastDurationMs,
                long maxDurationMs,
                long lastPssBeforeKb,
                long lastPssAfterKb,
                long peakPssKb,
                long lastHeapBeforeBytes,
                long lastHeapAfterBytes,
                long peakHeapBytes,
                long lastAtMs,
                String lastModel,
                String lastBackend,
                boolean lastSuccess,
                String lastError
        ) {
            this.count = Math.max(0L, count);
            this.success = Math.max(0L, success);
            this.failure = Math.max(0L, failure);
            this.totalDurationMs = Math.max(0L, totalDurationMs);
            this.lastDurationMs = Math.max(0L, lastDurationMs);
            this.maxDurationMs = Math.max(0L, maxDurationMs);
            this.lastPssBeforeKb = Math.max(0L, lastPssBeforeKb);
            this.lastPssAfterKb = Math.max(0L, lastPssAfterKb);
            this.peakPssKb = Math.max(0L, peakPssKb);
            this.lastHeapBeforeBytes = Math.max(0L, lastHeapBeforeBytes);
            this.lastHeapAfterBytes = Math.max(0L, lastHeapAfterBytes);
            this.peakHeapBytes = Math.max(0L, peakHeapBytes);
            this.lastAtMs = Math.max(0L, lastAtMs);
            this.lastModel = safe(lastModel);
            this.lastBackend = safe(lastBackend);
            this.lastSuccess = lastSuccess;
            this.lastError = safe(lastError);
        }

        long averageDurationMs() {
            return count <= 0L ? 0L : totalDurationMs / count;
        }

        String displayText() {
            if (count <= 0L) {
                return "まだ実測データはありません。CLEFを使った認知を実行すると自動記録されます。";
            }
            StringBuilder result = new StringBuilder();
            result.append("端末  ")
                    .append(Build.MANUFACTURER)
                    .append(" ")
                    .append(Build.MODEL)
                    .append("  ·  Android ")
                    .append(Build.VERSION.RELEASE)
                    .append(" / SDK ")
                    .append(Build.VERSION.SDK_INT)
                    .append("\n");
            result.append("実行  ")
                    .append(count)
                    .append("回  ·  成功 ")
                    .append(success)
                    .append("  ·  失敗 ")
                    .append(failure)
                    .append("\n");
            result.append("処理時間  直近 ")
                    .append(lastDurationMs)
                    .append(" ms  ·  平均 ")
                    .append(averageDurationMs())
                    .append(" ms  ·  最大 ")
                    .append(maxDurationMs)
                    .append(" ms\n");
            result.append("PSS  前 ")
                    .append(formatKiB(lastPssBeforeKb))
                    .append("  →  後 ")
                    .append(formatKiB(lastPssAfterKb))
                    .append("  ·  最大 ")
                    .append(formatKiB(peakPssKb))
                    .append("\n");
            result.append("Java heap  前 ")
                    .append(formatBytes(lastHeapBeforeBytes))
                    .append("  →  後 ")
                    .append(formatBytes(lastHeapAfterBytes))
                    .append("  ·  最大 ")
                    .append(formatBytes(peakHeapBytes))
                    .append("\n");
            result.append("直近  ")
                    .append(lastModel.isEmpty() ? "-" : lastModel)
                    .append("  ·  ")
                    .append(lastBackend.isEmpty() ? "backend不明" : lastBackend)
                    .append("  ·  ")
                    .append(lastSuccess ? "成功" : "失敗");
            if (!lastSuccess && !lastError.isEmpty()) {
                result.append("  ·  ").append(lastError);
            }
            return result.toString();
        }
    }

    private final SharedPreferences preferences;

    ClefPerformanceStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void record(
            String model,
            long durationMs,
            long pssBeforeKb,
            long pssAfterKb,
            long heapBeforeBytes,
            long heapAfterBytes,
            boolean success,
            Throwable error
    ) {
        record(model, "", durationMs, pssBeforeKb, pssAfterKb,
                heapBeforeBytes, heapAfterBytes, success, error);
    }

    void record(
            String model,
            String backend,
            long durationMs,
            long pssBeforeKb,
            long pssAfterKb,
            long heapBeforeBytes,
            long heapAfterBytes,
            boolean success,
            Throwable error
    ) {
        synchronized (LOCK) {
            long oldCount = preferences.getLong(COUNT, 0L);
            long oldSuccess = preferences.getLong(SUCCESS, 0L);
            long oldFailure = preferences.getLong(FAILURE, 0L);
            long oldTotal = preferences.getLong(TOTAL_DURATION_MS, 0L);
            long oldMaxDuration = preferences.getLong(MAX_DURATION_MS, 0L);
            long oldPeakPss = preferences.getLong(PEAK_PSS_KB, 0L);
            long oldPeakHeap = preferences.getLong(PEAK_HEAP_BYTES, 0L);

            long normalizedDuration = Math.max(0L, durationMs);
            long normalizedPssBefore = Math.max(0L, pssBeforeKb);
            long normalizedPssAfter = Math.max(0L, pssAfterKb);
            long normalizedHeapBefore = Math.max(0L, heapBeforeBytes);
            long normalizedHeapAfter = Math.max(0L, heapAfterBytes);

            preferences.edit()
                    .putLong(COUNT, oldCount + 1L)
                    .putLong(SUCCESS, oldSuccess + (success ? 1L : 0L))
                    .putLong(FAILURE, oldFailure + (success ? 0L : 1L))
                    .putLong(TOTAL_DURATION_MS, safeAdd(oldTotal, normalizedDuration))
                    .putLong(LAST_DURATION_MS, normalizedDuration)
                    .putLong(MAX_DURATION_MS, Math.max(oldMaxDuration, normalizedDuration))
                    .putLong(LAST_PSS_BEFORE_KB, normalizedPssBefore)
                    .putLong(LAST_PSS_AFTER_KB, normalizedPssAfter)
                    .putLong(PEAK_PSS_KB,
                            Math.max(oldPeakPss, Math.max(normalizedPssBefore, normalizedPssAfter)))
                    .putLong(LAST_HEAP_BEFORE_BYTES, normalizedHeapBefore)
                    .putLong(LAST_HEAP_AFTER_BYTES, normalizedHeapAfter)
                    .putLong(PEAK_HEAP_BYTES,
                            Math.max(oldPeakHeap,
                                    Math.max(normalizedHeapBefore, normalizedHeapAfter)))
                    .putLong(LAST_AT_MS, System.currentTimeMillis())
                    .putString(LAST_MODEL, ClefSettingsStore.normalizeModel(model))
                    .putString(LAST_BACKEND, safe(backend))
                    .putBoolean(LAST_SUCCESS, success)
                    .putString(LAST_ERROR, error == null ? "" : ProcessingQueueRegistry.rootMessage(error))
                    .apply();
        }
    }

    Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(
                    preferences.getLong(COUNT, 0L),
                    preferences.getLong(SUCCESS, 0L),
                    preferences.getLong(FAILURE, 0L),
                    preferences.getLong(TOTAL_DURATION_MS, 0L),
                    preferences.getLong(LAST_DURATION_MS, 0L),
                    preferences.getLong(MAX_DURATION_MS, 0L),
                    preferences.getLong(LAST_PSS_BEFORE_KB, 0L),
                    preferences.getLong(LAST_PSS_AFTER_KB, 0L),
                    preferences.getLong(PEAK_PSS_KB, 0L),
                    preferences.getLong(LAST_HEAP_BEFORE_BYTES, 0L),
                    preferences.getLong(LAST_HEAP_AFTER_BYTES, 0L),
                    preferences.getLong(PEAK_HEAP_BYTES, 0L),
                    preferences.getLong(LAST_AT_MS, 0L),
                    preferences.getString(LAST_MODEL, ""),
                    preferences.getString(LAST_BACKEND, ""),
                    preferences.getBoolean(LAST_SUCCESS, false),
                    preferences.getString(LAST_ERROR, ""));
        }
    }

    void clear() {
        synchronized (LOCK) {
            preferences.edit().clear().apply();
        }
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format(Locale.JAPAN, "%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format(Locale.JAPAN, "%.1f MiB", mib);
        return String.format(Locale.JAPAN, "%.2f GiB", mib / 1024.0);
    }

    static String formatKiB(long kib) {
        return formatBytes(Math.max(0L, kib) * 1024L);
    }

    private static long safeAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}