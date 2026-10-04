package com.sktpj.npcbrain;

final class ClefNativeBridge {
    private static final Object LOCK = new Object();

    static {
        System.loadLibrary("npcbrain_clef");
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
        synchronized (LOCK) {
            return nativeEvaluateGpuOnly(
                    modelPath,
                    state == null ? "" : state,
                    fieldIds,
                    instructions,
                    optionIds,
                    optionDescriptions,
                    optionCounts);
        }
    }

    static double[] evaluateCpuOnly(
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) {
        synchronized (LOCK) {
            return nativeEvaluateCpuOnly(
                    modelPath,
                    state == null ? "" : state,
                    fieldIds,
                    instructions,
                    optionIds,
                    optionDescriptions,
                    optionCounts);
        }
    }

    static void unload() {
        synchronized (LOCK) {
            nativeUnload();
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

    private ClefNativeBridge() {
    }
}
