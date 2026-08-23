package com.sktpj.npcbrain;

final class DungeonGameBoyFilter {
    static final int TARGET_WIDTH = 160;
    static final int TARGET_HEIGHT = 144;
    static final int BRIGHTNESS = 6;
    static final int CONTRAST_VALUE = 122;
    static final boolean DITHER = true;

    static final int[] PALETTE = {
            0xFF9BBC0F,
            0xFF8BAC0F,
            0xFF306230,
            0xFF0F380F
    };

    private static final int[][] BAYER_4X4 = {
            {0, 8, 2, 10},
            {12, 4, 14, 6},
            {3, 11, 1, 9},
            {15, 7, 13, 5}
    };

    private DungeonGameBoyFilter() {
    }

    static void apply(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            throw new IllegalArgumentException("invalid pixel buffer");
        }
        float contrast = CONTRAST_VALUE / 100.0f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int color = pixels[index];
                int r = (color >>> 16) & 0xFF;
                int g = (color >>> 8) & 0xFF;
                int b = color & 0xFF;

                float lum = (0.299f * r) + (0.587f * g) + (0.114f * b);
                lum = ((lum - 128.0f) * contrast) + 128.0f + BRIGHTNESS;
                if (DITHER) {
                    lum += (BAYER_4X4[y & 3][x & 3] - 7.5f) * 7.0f;
                }
                lum = clamp(lum, 0.0f, 255.0f);
                int paletteIndex = clampInt(3 - ((int) Math.floor(lum / 64.0f)), 0, 3);
                pixels[index] = PALETTE[paletteIndex];
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
