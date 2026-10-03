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
            String[] actionIds,
            String[] actionDescriptions
    ) {
        if (modelFile == null || !modelFile.isFile() || modelFile.length() <= 0L) {
            throw new IllegalStateException("ローカルCLEFモデルがありません");
        }
        if (actionIds == null
                || actionDescriptions == null
                || actionIds.length == 0
                || actionIds.length != actionDescriptions.length) {
            throw new IllegalArgumentException("CLEF action criteria are invalid");
        }
        synchronized (LOCK) {
            double[] scores = nativeDecide(
                    modelFile.getAbsolutePath(),
                    state == null ? "" : state,
                    Arrays.copyOf(actionIds, actionIds.length),
                    Arrays.copyOf(actionDescriptions, actionDescriptions.length));
            int expected = actionIds.length + 2;
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
            String[] actionIds,
            String[] actionDescriptions);

    private static native void nativeUnload();

    private ClefNativeRuntime() {
    }
}
