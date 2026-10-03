package com.sktpj.npcbrain;

import android.content.Context;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ClefLocalDownloadManager {
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
                return "モデル  ダウンロード済み · "
                        + LocalModelDownloadManager.formatBytes(downloadedBytes);
            }
            if (downloading) {
                if (totalBytes > 0L) {
                    int percent = (int) Math.min(100L, downloadedBytes * 100L / totalBytes);
                    return "モデル  ダウンロード中 " + percent + "% · "
                            + LocalModelDownloadManager.formatBytes(downloadedBytes)
                            + " / " + LocalModelDownloadManager.formatBytes(totalBytes);
                }
                return "モデル  ダウンロード中 · "
                        + LocalModelDownloadManager.formatBytes(downloadedBytes);
            }
            if (!errorMessage.isEmpty()) {
                return "モデル  ERROR · " + errorMessage;
            }
            return "モデル  未ダウンロード · 約6.49 GB";
        }
    }

    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "clef-local-download");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static volatile boolean downloading;
    private static volatile long downloadedBytes;
    private static volatile long totalBytes = -1L;
    private static volatile String errorMessage = "";

    private ClefLocalDownloadManager() {
    }

    static Snapshot snapshot(Context context) {
        ClefLocalModelRepository repository =
                new ClefLocalModelRepository(context.getApplicationContext());
        boolean ready = repository.isDownloaded();
        long installed = ready ? repository.installedBytes() : 0L;
        return new Snapshot(
                ready,
                !ready && downloading,
                ready ? installed : downloadedBytes,
                ready ? installed : totalBytes,
                ready ? "" : errorMessage);
    }

    static void startDownload(Context context) {
        Context appContext = context.getApplicationContext();
        ClefLocalModelRepository repository = new ClefLocalModelRepository(appContext);
        if (repository.isDownloaded()) return;
        synchronized (LOCK) {
            if (downloading) return;
            downloading = true;
            downloadedBytes = 0L;
            totalBytes = -1L;
            errorMessage = "";
        }

        String queueId = ProcessingQueueRegistry.enqueue(
                "local_model_download",
                "",
                ClefLocalModelRepository.MODEL_ID,
                System.currentTimeMillis());
        EXECUTOR.execute(() -> {
            ProcessingQueueRegistry.markRunning(queueId);
            try {
                File file = repository.ensureModel((downloaded, total) -> {
                    downloadedBytes = downloaded;
                    totalBytes = total;
                });
                downloadedBytes = file.length();
                totalBytes = file.length();
                errorMessage = "";
                ProcessingQueueRegistry.markCompleted(
                        queueId,
                        System.currentTimeMillis(),
                        ClefLocalModelRepository.MODEL_ID + " · "
                                + LocalModelDownloadManager.formatBytes(file.length()));
            } catch (Exception error) {
                String detail = error.getMessage();
                if (detail == null || detail.trim().isEmpty()) {
                    detail = error.getClass().getSimpleName();
                }
                errorMessage = detail;
                ProcessingQueueRegistry.markFailed(queueId, error);
            } finally {
                downloading = false;
            }
        });
    }

    static boolean deleteModel(Context context) {
        synchronized (LOCK) {
            if (downloading) return false;
            boolean deleted = new ClefLocalModelRepository(
                    context.getApplicationContext()).deleteModel();
            if (deleted) {
                downloadedBytes = 0L;
                totalBytes = -1L;
                errorMessage = "";
            }
            return deleted;
        }
    }

    static boolean isDownloading() {
        return downloading;
    }
}
