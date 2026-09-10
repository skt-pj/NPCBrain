package com.sktpj.npcbrain;

import android.content.Context;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-process download coordinator for shared local model files. */
final class LocalModelDownloadManager {
    static final class Snapshot {
        final boolean downloaded;
        final boolean downloading;
        final long downloadedBytes;
        final long totalBytes;
        final String errorMessage;

        Snapshot(
                boolean downloaded,
                boolean downloading,
                long downloadedBytes,
                long totalBytes,
                String errorMessage
        ) {
            this.downloaded = downloaded;
            this.downloading = downloading;
            this.downloadedBytes = Math.max(0L, downloadedBytes);
            this.totalBytes = totalBytes;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        String displayText() {
            if (downloaded) {
                return "モデルデータ  ダウンロード済み · " + formatBytes(downloadedBytes);
            }
            if (downloading) {
                if (totalBytes > 0L) {
                    int percent = (int) Math.min(100L, downloadedBytes * 100L / totalBytes);
                    return "モデルデータ  ダウンロード中 " + percent + "% · "
                            + formatBytes(downloadedBytes) + " / " + formatBytes(totalBytes);
                }
                return "モデルデータ  ダウンロード中 · " + formatBytes(downloadedBytes);
            }
            if (!errorMessage.isEmpty()) {
                return "モデルデータ  ERROR · " + errorMessage;
            }
            return "モデルデータ  未ダウンロード";
        }
    }

    private static final class MutableState {
        volatile boolean downloading;
        volatile long downloadedBytes;
        volatile long totalBytes = -1L;
        volatile String errorMessage = "";
    }

    private static final Map<String, MutableState> STATES = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "local-model-download");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private LocalModelDownloadManager() {
    }

    static Snapshot snapshot(Context context, String modelId) {
        String model = NpcInferenceModel.normalize(modelId);
        if (!NpcInferenceModel.isLocal(model)) {
            throw new IllegalArgumentException("OpenAI model has no local download: " + model);
        }
        LocalModelRepository repository = new LocalModelRepository(context.getApplicationContext());
        MutableState state = STATES.get(model);
        boolean ready = repository.isDownloaded(model);
        long installed = ready ? repository.installedBytes(model) : 0L;
        if (state == null) {
            return new Snapshot(ready, false, installed, ready ? installed : -1L, "");
        }
        return new Snapshot(
                ready,
                !ready && state.downloading,
                ready ? installed : state.downloadedBytes,
                ready ? installed : state.totalBytes,
                ready ? "" : state.errorMessage);
    }

    static void startDownload(Context context, String modelId) {
        String model = NpcInferenceModel.normalize(modelId);
        if (!NpcInferenceModel.isLocal(model)) {
            throw new IllegalArgumentException("OpenAI model has no local download: " + model);
        }
        Context appContext = context.getApplicationContext();
        LocalModelRepository repository = new LocalModelRepository(appContext);
        if (repository.isDownloaded(model)) return;

        MutableState state = STATES.computeIfAbsent(model, ignored -> new MutableState());
        synchronized (state) {
            if (state.downloading) return;
            state.downloading = true;
            state.downloadedBytes = 0L;
            state.totalBytes = -1L;
            state.errorMessage = "";
        }

        String queueId = ProcessingQueueRegistry.enqueue(
                "local_model_download", "", model, System.currentTimeMillis());
        EXECUTOR.execute(() -> {
            ProcessingQueueRegistry.markRunning(queueId);
            try {
                File file = repository.ensureModel(model, (downloaded, total) -> {
                    state.downloadedBytes = downloaded;
                    state.totalBytes = total;
                });
                state.downloadedBytes = file.length();
                state.totalBytes = file.length();
                state.errorMessage = "";
                ProcessingQueueRegistry.markCompleted(
                        queueId, System.currentTimeMillis(), model + " · " + formatBytes(file.length()));
            } catch (Exception error) {
                String detail = error.getMessage();
                if (detail == null || detail.trim().isEmpty()) {
                    detail = error.getClass().getSimpleName();
                }
                state.errorMessage = detail;
                ProcessingQueueRegistry.markFailed(queueId, error);
            } finally {
                state.downloading = false;
            }
        });
    }

    static boolean anyDownloading() {
        for (MutableState state : STATES.values()) {
            if (state.downloading) return true;
        }
        return false;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format(Locale.JAPAN, "%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format(Locale.JAPAN, "%.1f MiB", mib);
        return String.format(Locale.JAPAN, "%.2f GiB", mib / 1024.0);
    }
}
