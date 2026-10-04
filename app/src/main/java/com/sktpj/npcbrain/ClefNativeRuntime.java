package com.sktpj.npcbrain;

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
            File modelFile,
            String state,
            List<ClefSpecialistSchema.Field> fields
    ) {
        return evaluate(modelFile, state, fields, true);
    }

    static double[] evaluate(
            File modelFile,
            String state,
            List<ClefSpecialistSchema.Field> fields,
            boolean preferGpu
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

        synchronized (LOCK) {
            double[] scores = nativeEvaluate(
                    modelFile.getAbsolutePath(),
                    state == null ? "" : state,
                    fieldIds,
                    instructions,
                    optionIds.toArray(new String[0]),
                    optionDescriptions.toArray(new String[0]),
                    optionCounts,
                    preferGpu);
            if (scores == null || scores.length != expectedScores) {
                throw new IllegalStateException(
                        "CLEF native score countが不正です: "
                                + (scores == null ? 0 : scores.length)
                                + " / expected " + expectedScores);
            }
            return scores;
        }
    }

    /**
     * Backward-compatible path for the old action-selection runtime.
     * New split-brain execution uses evaluate().
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
        return evaluate(modelFile, state, Arrays.asList(action, commit), true);
    }

    static void unload() {
        synchronized (LOCK) {
            nativeUnload();
        }
    }

    static String backendInfo() {
        synchronized (LOCK) {
            String value = nativeBackendInfo();
            return value == null || value.trim().isEmpty() ? "not_loaded" : value.trim();
        }
    }

    static String gpuFallbackReason() {
        synchronized (LOCK) {
            String value = nativeGpuFallbackReason();
            return value == null ? "" : value.trim();
        }
    }

    private static native double[] nativeEvaluate(
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts,
            boolean preferGpu);

    private static native String nativeBackendInfo();

    private static native String nativeGpuFallbackReason();

    private static native void nativeUnload();

    private ClefNativeRuntime() {
    }
}