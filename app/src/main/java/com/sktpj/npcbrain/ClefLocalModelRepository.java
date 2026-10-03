package com.sktpj.npcbrain;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads the joint-head-integrated CLEF-Flash GGUF into app-private storage. */
final class ClefLocalModelRepository {
    interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    static final String MODEL_ID = "clef-flash-q4_k_m";
    static final String REPOSITORY = "ggml-org/Clef-Flash-GGUF";
    static final String REVISION = "main";
    static final String FILE_NAME = "Clef-Flash-Q4_K_M.gguf";
    static final long APPROXIMATE_SIZE_BYTES = 6_490_000_000L;

    private static final Object DOWNLOAD_LOCK = new Object();

    private final Context appContext;

    ClefLocalModelRepository(Context context) {
        appContext = context.getApplicationContext();
    }

    static String downloadUrl() {
        return "https://huggingface.co/" + REPOSITORY
                + "/resolve/" + REVISION + "/" + FILE_NAME + "?download=true";
    }

    File modelFile() {
        File directory = new File(appContext.getFilesDir(), "clef");
        return new File(directory, FILE_NAME);
    }

    boolean isDownloaded() {
        File file = modelFile();
        return file.isFile() && file.length() > 1024L * 1024L * 1024L;
    }

    long installedBytes() {
        return isDownloaded() ? modelFile().length() : 0L;
    }

    File ensureModel(ProgressListener listener) throws IOException {
        synchronized (DOWNLOAD_LOCK) {
            File target = modelFile();
            if (isDownloaded()) {
                notifyProgress(listener, target.length(), target.length());
                return target;
            }

            File parent = target.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                throw new IOException("CLEF保存先を作成できません: " + target);
            }
            File part = new File(parent, FILE_NAME + ".part");
            if (part.exists() && !part.delete()) {
                throw new IOException("CLEF一時ファイルを削除できません: " + part);
            }

            HttpURLConnection connection = openFollowingRedirects(new URL(downloadUrl()));
            long contentLength = connection.getContentLengthLong();
            long written = 0L;
            notifyProgress(listener, 0L, contentLength);
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(part, false)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count == 0) continue;
                    output.write(buffer, 0, count);
                    written += count;
                    notifyProgress(listener, written, contentLength);
                }
                output.getFD().sync();
            } finally {
                connection.disconnect();
            }

            if (written <= 1024L * 1024L * 1024L
                    || (contentLength > 0L && written != contentLength)) {
                part.delete();
                throw new IOException("CLEF download sizeが不正です: " + written
                        + (contentLength > 0L ? " / expected " + contentLength : ""));
            }
            if (target.exists() && !target.delete()) {
                part.delete();
                throw new IOException("既存CLEFモデルを置換できません");
            }
            if (!part.renameTo(target)) {
                part.delete();
                throw new IOException("CLEFモデルを確定保存できません");
            }
            notifyProgress(listener, target.length(), target.length());
            return target;
        }
    }

    boolean deleteModel() {
        synchronized (DOWNLOAD_LOCK) {
            ClefNativeRuntime.unload();
            File target = modelFile();
            File part = new File(target.getParentFile(), FILE_NAME + ".part");
            boolean ok = true;
            if (target.exists()) ok &= target.delete();
            if (part.exists()) ok &= part.delete();
            return ok;
        }
    }

    private static void notifyProgress(ProgressListener listener, long downloaded, long total) {
        if (listener == null) return;
        try {
            listener.onProgress(Math.max(0L, downloaded), total);
        } catch (RuntimeException ignored) {
        }
    }

    private static HttpURLConnection openFollowingRedirects(URL initial) throws IOException {
        URL current = initial;
        for (int i = 0; i < 8; i++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/octet-stream");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("CLEF download redirect先がありません");
                }
                current = new URL(current, location);
                continue;
            }
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED
                    || status == HttpURLConnection.HTTP_FORBIDDEN) {
                connection.disconnect();
                throw new IOException("CLEF配布元が認証を要求しています (HTTP " + status + ")");
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("CLEF download failed: HTTP " + status);
            }
            return connection;
        }
        throw new IOException("CLEF download redirectが多すぎます");
    }
}
