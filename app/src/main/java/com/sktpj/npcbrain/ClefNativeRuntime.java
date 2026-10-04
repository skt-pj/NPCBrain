package com.sktpj.npcbrain;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ClefNativeRuntime {
    private static final Object LOCK = new Object();

    static {
        System.loadLibrary("npcbrain_clef");
    }

    static double[] evaluate(
            Context context,
            File modelFile,
            String state,
            List<ClefSpecialistSchema.Field> fields
    ) {
        if (context == null) throw new IllegalArgumentException("context is required");
        Payload payload = buildPayload(modelFile, state, fields);

        try {
            double[] scores = ClefGpuProcessClient.evaluate(
                    context,
                    payload.modelPath,
                    payload.state,
                    payload.fieldIds,
                    payload.instructions,
                    payload.optionIds,
                    payload.optionDescriptions,
                    payload.optionCounts);
            verifyScoreCount(scores, payload.expectedScores);
            return scores;
        } catch (Exception gpuFailure) {
            return evaluateCpuOnly(payload);
        }
    }

    /**
     * Backward-compatible path for any legacy local action-selection caller.
     * It is deliberately CPU-only because it has no Context with which to use the crash-contained
     * GPU service. Current split-brain production calls evaluate(Context, ...).
     */
    static double[] decide(
            File modelFile,
            String state,
            String[] actionIds,
            String[] actionDescriptions
    ) {
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
        Payload payload = buildPayload(
                modelFile,
                state,
                Arrays.asList(action, commit));
        return evaluateCpuOnly(payload);
    }

    static double[] evaluateGpuOnly(
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) {
        int expectedScores = validateRawPayload(
                modelPath,
                fieldIds,
                instructions,
                optionIds,
                optionDescriptions,
                optionCounts);
        synchronized (LOCK) {
            double[] scores = nativeEvaluateGpuOnly(
                    modelPath,
                    state == null ? "" : state,
                    fieldIds,
                    instructions,
                    optionIds,
                    optionDescriptions,
                    optionCounts);
            verifyScoreCount(scores, expectedScores);
            return scores;
        }
    }

    static void unload() {
        synchronized (LOCK) {
            nativeUnload();
        }
    }

    private static double[] evaluateCpuOnly(Payload payload) {
        synchronized (LOCK) {
            double[] scores = nativeEvaluateCpuOnly(
                    payload.modelPath,
                    payload.state,
                    payload.fieldIds,
                    payload.instructions,
                    payload.optionIds,
                    payload.optionDescriptions,
                    payload.optionCounts);
            verifyScoreCount(scores, payload.expectedScores);
            return scores;
        }
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

    private static int validateRawPayload(
            String modelPath,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) {
        if (modelPath == null || modelPath.trim().isEmpty()) {
            throw new IllegalArgumentException("CLEF model path is required");
        }
        if (fieldIds == null
                || instructions == null
                || optionIds == null
                || optionDescriptions == null
                || optionCounts == null
                || fieldIds.length == 0
                || fieldIds.length != instructions.length
                || fieldIds.length != optionCounts.length
                || optionIds.length != optionDescriptions.length) {
            throw new IllegalArgumentException("CLEF JNI schema shape is invalid");
        }
        int expectedScores = 0;
        for (int count : optionCounts) {
            if (count < 2) throw new IllegalArgumentException("CLEF JNI option count is invalid");
            expectedScores += count;
        }
        if (expectedScores != optionIds.length) {
            throw new IllegalArgumentException("CLEF JNI flattened options are invalid");
        }
        return expectedScores;
    }

    private static void verifyScoreCount(double[] scores, int expectedScores) {
        if (scores == null || scores.length != expectedScores) {
            throw new IllegalStateException(
                    "CLEF native score countが不正です: "
                            + (scores == null ? 0 : scores.length)
                            + " / expected " + expectedScores);
        }
    }

    private static native double[] nativeEvaluateGpuOnly(
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts);

    private static native double[] nativeEvaluateCpuOnly(
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts);

    private static native void nativeUnload();

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
