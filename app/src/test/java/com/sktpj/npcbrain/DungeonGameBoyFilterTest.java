package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DungeonGameBoyFilterTest {
    @Test
    public void gbModerDefaultsArePreserved() {
        assertEquals(160, DungeonGameBoyFilter.TARGET_WIDTH);
        assertEquals(144, DungeonGameBoyFilter.TARGET_HEIGHT);
        assertEquals(6, DungeonGameBoyFilter.BRIGHTNESS);
        assertEquals(122, DungeonGameBoyFilter.CONTRAST_VALUE);
        assertTrue(DungeonGameBoyFilter.DITHER);
        assertEquals(0xFF9BBC0F, DungeonGameBoyFilter.PALETTE[0]);
        assertEquals(0xFF8BAC0F, DungeonGameBoyFilter.PALETTE[1]);
        assertEquals(0xFF306230, DungeonGameBoyFilter.PALETTE[2]);
        assertEquals(0xFF0F380F, DungeonGameBoyFilter.PALETTE[3]);
    }

    @Test
    public void blackAndWhiteMapToDarkestAndLightestPaletteColors() {
        int[] pixels = {0xFF000000, 0xFFFFFFFF};
        DungeonGameBoyFilter.apply(pixels, 2, 1);
        assertEquals(0xFF0F380F, pixels[0]);
        assertEquals(0xFF9BBC0F, pixels[1]);
    }

    @Test
    public void bayerCoordinateChangesMidGrayQuantization() {
        int[] pixels = {0xFF808080, 0xFF808080};
        DungeonGameBoyFilter.apply(pixels, 2, 1);
        assertEquals(0xFF306230, pixels[0]);
        assertEquals(0xFF8BAC0F, pixels[1]);
    }

    @Test
    public void everyOutputPixelBelongsToFourColorPalette() {
        int[] pixels = {
                0x00112233, 0x7F445566, 0xFF778899, 0xFFAABBCC,
                0xFF102030, 0xFF405060, 0xFF708090, 0xFFDDEEFF
        };
        DungeonGameBoyFilter.apply(pixels, 4, 2);
        for (int pixel : pixels) {
            assertTrue(isPaletteColor(pixel));
            assertEquals(0xFF000000, pixel & 0xFF000000);
        }
    }

    private static boolean isPaletteColor(int pixel) {
        for (int color : DungeonGameBoyFilter.PALETTE) {
            if (pixel == color) return true;
        }
        return false;
    }
}
