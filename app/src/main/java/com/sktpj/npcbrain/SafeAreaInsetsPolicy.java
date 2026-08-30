package com.sktpj.npcbrain;

final class SafeAreaInsetsPolicy {
    private SafeAreaInsetsPolicy() {
    }

    static Padding resolve(
            int baseLeft,
            int baseTop,
            int baseRight,
            int baseBottom,
            int safeLeft,
            int safeTop,
            int safeRight,
            int safeBottom
    ) {
        return new Padding(
                nonNegative(baseLeft) + nonNegative(safeLeft),
                nonNegative(baseTop) + nonNegative(safeTop),
                nonNegative(baseRight) + nonNegative(safeRight),
                nonNegative(baseBottom) + nonNegative(safeBottom));
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    static final class Padding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Padding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
