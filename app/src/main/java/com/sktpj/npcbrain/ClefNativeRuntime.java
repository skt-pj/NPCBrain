package com.sktpj.npcbrain;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ClefNativeRuntime {
    static double[] evaluate(
            Context context,
            File modelFile,
            String state,
            List<ClefSpecialistSchema.Field> fields,
            String executionBackend
    ) throws Exception {
        if (context == null) throw new IllegalArgumentException("context is required");
        String backend = ClefSettingsStore.normalizeExecutionBackend(executionBackend);
        if (backend.isEmpty()) {
            throw new IllegalStateException(
                    "CLEF実行方式が未選択です。AI管理でGPUまたはCPUを選択してください。");
        }

        Payload payload = buildPayload(modelFile, state, fields);
        double[] scores;
        if (ClefSettingsStore.EXECUTION_GPU.equals(executionBackend)) {
            scores = ClefGpuProcessClient.evaluateGpu(
                    context,
                    payload.modelPath,
                    payload.state,
                    payload.fieldIds,
                    payload.instructions,
                    payload.optionIds,
                    payload.optionDescriptions,
                    payload.optionCounts);
        } else if (ClefSettingsStore.EXECUTION_CPU.equals(executionBackend)) {
            scores = ClefGpuProcessClient.evaluateCpu(
                    context,
                    payload.modelPath,
                    payload.state,
                    payload.fieldIds,
                    payload.instructions,
                    payload.optionIds,
                    payload.optionDescriptions,
                    payload.optionCounts);
        } else {
            throw new IllegalStateException("Unsupported CLEF execution backend: " + executionBackend);
        }
        verifyScoreCount(scores, payload.expectedScores);
        return scores;
    }

    /**
     * Backward-compatible action-selection entry. Native execution remains isolated in the
     * secondary CLEF process instead of running inside the caller process.
     */
    static double[] decide(
            File modelFile,
            String state,
            String[] actionIds,
            String[] actionDescriptions
    ) throws Exception {
        Context context = NPCBrainApplication.applicationContextForRuntime();
        if (context == null) {
            throw new IllegalStateException("NPCBrain application context is unavailable");
        }
        if (actionIds == null
                || actionDescriptions == null
                || actionIds.length == 0
                || actionIds.length != actionDescriptions.length) {
            throw new IllegalArgumentException("CLEF action criteria are invalid");
        }
        ClefSpecialistSchema.Field action = new ClefSpecialistSchema.Field(
                "action",
                "Choose the single action class this NPC should take now.",
                actionIds,
                actionDescriptions);
        ClefSpecialistSchema.Field commit = new ClefSpecialistSchema.Field(
                "commit_now",
                "Should this NPC commit to the chosen action class now?",
                new String[]{"true", "false"},
                new String[]{"Yes.", "No."});
        String backend = new ClefSettingsStore(context).executionBackend();
        return evaluate(context, modelFile, state, Arrays.asList(action, commit), backend);
    }

    static void unload() {
        Context context = NPCBrainApplication.applicationContextForRuntime();
        if (context != null) ClefGpuProcessClient.shutdown(context);
    }

    private static Payload buildPayload(
            File modelFile,
            String state,
            List<ClefSpecialistSchema.Field> fields
    ) {
        if (modelFile == null || !modelFile.isFile() || modelFile.length() <= 0L) {
            throw new IllegalStateException("ローカルCLEFモデルがありません");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("CLEF specialist fields are empty");
        }

        String[] fieldIds = new String[fields.size()];
        String[] instructions = new String[fields.size()];
        int[] optionCounts = new int[fields.size()];
        List<String> optionIds = new ArrayList<>();
        List<String> optionDescriptions = new ArrayList<>();
        int expectedScores = 0;

        for (int i = 0; i < fields.size(); i++) {
            ClefSpecialistSchema.Field field = fields.get(i);
            if (field == null
                    || field.id == null
                    || field.id.trim().isEmpty()
                    || field.instruction == null
                    || field.optionIds == null
                    || field.optionDescriptions == null
                    || field.optionIds.length < 2
                    || field.optionIds.length != field.optionDescriptions.length) {
                throw new IllegalArgumentException("CLEF specialist field is invalid");
            }
            fieldIds[i] = field.id;
            instructions[i] = field.instruction;
            optionCounts[i] = field.optionIds.length;
            expectedScores += field.optionIds.length;
            optionIds.addAll(Arrays.asList(field.optionIds));
            optionDescriptions.addAll(Arrays.asList(field.optionDescriptions));
        }

        return new Payload(
                modelFile.getAbsolutePath(),
                state == null ? "" : state,
                fieldIds,
                instructions,
                optionIds.toArray(new String[0]),
                optionDescriptions.toArray(new String[0]),
                optionCounts,
                expectedScores);
    }

    private static void verifyScoreCount(double[] scores, int expectedScores) {
        if (scores == null || scores.length != expectedScores) {
            throw new IllegalStateException(
                    "CLEF native score countが不正です: "
                            + (scores == null ? 0 : scores.length)
                            + " / expected " + expectedScores);
        }
    }

    private static final class Payload {
        final String modelPath;
        final String state;
        final String[] fieldIds;
        final String[] instructions;
        final String[] optionIds;
        final String[] optionDescriptions;
        final int[] optionCounts;
        final int expectedScores;

        Payload(
                String modelPath,
                String state,
                String[] fieldIds,
                String[] instructions,
                String[] optionIds,
                String[] optionDescriptions,
                int[] optionCounts,
                int expectedScores
        ) {
            this.modelPath = modelPath;
            this.state = state;
            this.fieldIds = fieldIds;
            this.instructions = instructions;
            this.optionIds = optionIds;
            this.optionDescriptions = optionDescriptions;
            this.optionCounts = optionCounts;
            this.expectedScores = expectedScores;
        }
    }

    private ClefNativeRuntime() {
    }
}
