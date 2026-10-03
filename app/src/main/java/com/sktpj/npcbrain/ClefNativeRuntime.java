package com.sktpj.npcbrain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ClefNativeRuntime {
    private static final Object LOCK = new Object();

    static {
        System.loadLibrary("npcbrain_clef");
    }

    static double[] decideBatch(
            File modelFile,
            String state,
            SpecialistDecisionSchema.Spec[] specs
    ) {
        if (modelFile == null || !modelFile.isFile() || modelFile.length() <= 0L) {
            throw new IllegalStateException("ローカルCLEFモデルがありません");
        }
        if (specs == null || specs.length == 0) {
            throw new IllegalArgumentException("CLEF decision questions are required");
        }

        String[] questionIds = new String[specs.length];
        String[] questionInstructions = new String[specs.length];
        String[] evidenceInstructions = new String[specs.length];
        int[] optionCounts = new int[specs.length];
        List<String> optionIds = new ArrayList<>();
        List<String> optionDescriptions = new ArrayList<>();
        int expected = 0;
        for (int i = 0; i < specs.length; i++) {
            SpecialistDecisionSchema.Spec spec = specs[i];
            if (spec == null || spec.criteria.isEmpty()) {
                throw new IllegalArgumentException("CLEF decision criteria are invalid");
            }
            questionIds[i] = spec.questionId;
            questionInstructions[i] = spec.instruction;
            evidenceInstructions[i] = spec.evidenceInstruction;
            String[] ids = spec.optionIds();
            String[] descriptions = spec.optionDescriptions();
            optionCounts[i] = ids.length;
            expected += ids.length + 2;
            for (int j = 0; j < ids.length; j++) {
                optionIds.add(ids[j]);
                optionDescriptions.add(descriptions[j]);
            }
        }

        synchronized (LOCK) {
            double[] scores = nativeDecideBatch(
                    modelFile.getAbsolutePath(),
                    state == null ? "" : state,
                    questionIds,
                    questionInstructions,
                    evidenceInstructions,
                    optionCounts,
                    optionIds.toArray(new String[0]),
                    optionDescriptions.toArray(new String[0]));
            if (scores == null || scores.length != expected) {
                throw new IllegalStateException(
                        "CLEF native score countが不正です: "
                                + (scores == null ? 0 : scores.length)
                                + " / expected " + expected);
            }
            return scores;
        }
    }

    static void unload() {
        synchronized (LOCK) {
            nativeUnload();
        }
    }

    private static native double[] nativeDecideBatch(
            String modelPath,
            String state,
            String[] questionIds,
            String[] questionInstructions,
            String[] evidenceInstructions,
            int[] optionCounts,
            String[] optionIds,
            String[] optionDescriptions);

    private static native void nativeUnload();

    private ClefNativeRuntime() {
    }
}
