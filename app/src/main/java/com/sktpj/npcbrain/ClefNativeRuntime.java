package com.sktpj.npcbrain;

import java.io.File;
import java.util.Arrays;

final class ClefNativeRuntime {
    private static final Object LOCK = new Object();

    static {
        System.loadLibrary("npcbrain_clef");
    }

    static double[] decide(
            File modelFile,
            String state,
            String questionId,
            String questionInstruction,
            String evidenceInstruction,
            String[] optionIds,
            String[] optionDescriptions
    ) {
        if (modelFile == null || !modelFile.isFile() || modelFile.length() <= 0L) {
            throw new IllegalStateException("ローカルCLEFモデルがありません");
        }
        if (optionIds == null
                || optionDescriptions == null
                || optionIds.length == 0
                || optionIds.length != optionDescriptions.length) {
            throw new IllegalArgumentException("CLEF decision criteria are invalid");
        }
        synchronized (LOCK) {
            double[] scores = nativeDecide(
                    modelFile.getAbsolutePath(),
                    state == null ? "" : state,
                    questionId == null ? "decision" : questionId,
                    questionInstruction == null ? "" : questionInstruction,
                    evidenceInstruction == null ? "" : evidenceInstruction,
                    Arrays.copyOf(optionIds, optionIds.length),
                    Arrays.copyOf(optionDescriptions, optionDescriptions.length));
            int expected = optionIds.length + 2;
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

    private static native double[] nativeDecide(
            String modelPath,
            String state,
            String questionId,
            String questionInstruction,
            String evidenceInstruction,
            String[] optionIds,
            String[] optionDescriptions);

    private static native void nativeUnload();

    private ClefNativeRuntime() {
    }
}
