package com.sktpj.npcbrain;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Downloads the fixed LiteRT-LM model artifacts into app-private storage. */
final class LocalModelRepository {
    static final class ModelSpec {
        final String modelId;
        final String repository;
        final String revision;
        final String fileName;
        final long expectedSizeBytes;

        ModelSpec(
                String modelId,
                String repository,
                String revision,
                String fileName,
                long expectedSizeBytes
        ) {
            this.modelId = modelId;
            this.repository = repository;
            this.revision = revision;
            this.fileName = fileName;
            this.expectedSizeBytes = expectedSizeBytes;
        }

        String downloadUrl() {
            return "https://huggingface.co/" + repository + "/resolve/" + revision + "/" + fileName;
        }
    }

    private static final Map<String, ModelSpec> SPECS;

    static {
        Map<String, ModelSpec> specs = new LinkedHashMap<>();
        specs.put(NpcInferenceModel.LOCAL_LIGHT, new ModelSpec(
                NpcInferenceModel.LOCAL_LIGHT,
                "litert-community/Gemma3-1B-IT",
                "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
                "gemma3-1b-it-int4.litertlm",
                -1L));
        specs.put(NpcInferenceModel.LOCAL_MEDIUM, new ModelSpec(
                NpcInferenceModel.LOCAL_MEDIUM,
                "litert-community/Qwen2.5-1.5B-Instruct",
                "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                -1L));
        specs.put(NpcInferenceModel.LOCAL_HEAVY, new ModelSpec(
                NpcInferenceModel.LOCAL_HEAVY,
                "litert-community/gemma-4-E2B-it-litert-lm",
                "6e5c4f1e395deb959c494953478fa5cec4b8008f",
                "gemma-4-E2B-it.litertlm",
                -1L));
        SPECS = Collections.unmodifiableMap(specs);
    }

    private final Context appContext;

    LocalModelRepository(Context context) {
        appContext = context.getApplicationContext();
    }

    static ModelSpec spec(String modelId) {
        String normalized = NpcInferenceModel.normalize(modelId);
        ModelSpec result = SPECS.get(normalized);
        if (result == null) {
            throw new IllegalArgumentException("OpenAI model is not a local model: " + normalized);
        }
        return result;
    }

    File modelFile(String modelId) {
        ModelSpec spec = spec(modelId);
        File directory = new File(new File(appContext.getFilesDir(), "local_llm"), spec.modelId);
        return new File(directory, spec.fileName);
    }

    File ensureModel(String modelId) throws IOException {
        ModelSpec spec = spec(modelId);
        File target = modelFile(spec.modelId);
        if (isUsable(target, spec.expectedSizeBytes)) return target;

        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("ローカルLLM保存先を作成できません: " + target);
        }
        File part = new File(parent, spec.fileName + ".part");
        if (part.exists() && !part.delete()) {
            throw new IOException("ローカルLLMの一時ファイルを削除できません: " + part);
        }

        HttpURLConnection connection = openFollowingRedirects(new URL(spec.downloadUrl()));
        long contentLength = connection.getContentLengthLong();
        long written = 0L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(part, false)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
                written += count;
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }

        long required = spec.expectedSizeBytes > 0L ? spec.expectedSizeBytes : contentLength;
        if (written <= 0L || (required > 0L && written != required)) {
            part.delete();
            throw new IOException("ローカルLLMのdownload sizeが不正です: " + written
                    + (required > 0L ? " / expected " + required : ""));
        }
        if (target.exists() && !target.delete()) {
            part.delete();
            throw new IOException("既存ローカルLLMを置換できません: " + target);
        }
        if (!part.renameTo(target)) {
            part.delete();
            throw new IOException("ローカルLLMを確定保存できません: " + target);
        }
        return target;
    }

    private static boolean isUsable(File file, long expectedSizeBytes) {
        if (file == null || !file.isFile() || file.length() <= 0L) return false;
        return expectedSizeBytes <= 0L || file.length() == expectedSizeBytes;
    }

    private static HttpURLConnection openFollowingRedirects(URL initial) throws IOException {
        URL current = initial;
        for (int i = 0; i < 6; i++) {
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
                    throw new IOException("ローカルLLM download redirect先がありません");
                }
                current = new URL(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("ローカルLLM download failed: HTTP " + status);
            }
            return connection;
        }
        throw new IOException("ローカルLLM download redirectが多すぎます");
    }
}
