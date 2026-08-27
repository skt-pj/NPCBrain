package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DungeonBoardPaletteTest {
    @Test
    public void visibleFloorIsSeparatedFromStructureAndFog() {
        int minVisibleFloor = Math.min(
                DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_A),
                DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_B));
        int visibleWall = DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_WALL_BODY);
        int maxHiddenFloor = Math.max(
                DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_A),
                DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_B));

        assertTrue(minVisibleFloor - visibleWall >= 50);
        assertTrue(minVisibleFloor - maxHiddenFloor >= 70);
    }

    @Test
    public void floorTextureStaysLowContrast() {
        assertEquals(12, Math.abs(
                DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_A)
                        - DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_B)));
        assertTrue(Math.abs(
                DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_A)
                        - DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_B)) <= 16);
        assertEquals(8, Math.abs(
                DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_A)
                        - DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_B)));
        assertTrue(Math.abs(
                DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_A)
                        - DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_B)) <= 16);
    }

    @Test
    public void fogHierarchyKeepsUnexploredDarkest() {
        int unexplored = DungeonBoardPalette.luma(DungeonBoardPalette.UNEXPLORED_FILL);
        int minHiddenFloor = Math.min(
                DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_A),
                DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_FLOOR_B));
        int minVisibleFloor = Math.min(
                DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_A),
                DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_FLOOR_B));

        assertTrue(unexplored < minHiddenFloor);
        assertTrue(minHiddenFloor < minVisibleFloor);
        assertTrue(minHiddenFloor - unexplored >= 50);
    }

    @Test
    public void wallKeepsStructuralToneOrder() {
        assertTrue(DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_WALL_TOP)
                > DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_WALL_BODY));
        assertTrue(DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_WALL_BODY)
                > DungeonBoardPalette.luma(DungeonBoardPalette.VISIBLE_WALL_BOTTOM));
        assertTrue(DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_WALL_TOP)
                > DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_WALL_BODY));
        assertTrue(DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_WALL_BODY)
                > DungeonBoardPalette.luma(DungeonBoardPalette.HIDDEN_WALL_BOTTOM));
    }

    @Test
    public void paletteColorsAreOpaqueGrayscale() {
        int[] colors = {
                DungeonBoardPalette.UNEXPLORED_FILL,
                DungeonBoardPalette.UNEXPLORED_MARK,
                DungeonBoardPalette.VISIBLE_FLOOR_A,
                DungeonBoardPalette.VISIBLE_FLOOR_B,
                DungeonBoardPalette.VISIBLE_FLOOR_BORDER,
                DungeonBoardPalette.HIDDEN_FLOOR_A,
                DungeonBoardPalette.HIDDEN_FLOOR_B,
                DungeonBoardPalette.HIDDEN_FLOOR_BORDER,
                DungeonBoardPalette.VISIBLE_WALL_BODY,
                DungeonBoardPalette.VISIBLE_WALL_TOP,
                DungeonBoardPalette.VISIBLE_WALL_BOTTOM,
                DungeonBoardPalette.HIDDEN_WALL_BODY,
                DungeonBoardPalette.HIDDEN_WALL_TOP,
                DungeonBoardPalette.HIDDEN_WALL_BOTTOM
        };
        for (int color : colors) {
            assertEquals(0xFF, (color >>> 24) & 0xFF);
            assertEquals((color >>> 16) & 0xFF, (color >>> 8) & 0xFF);
            assertEquals((color >>> 8) & 0xFF, color & 0xFF);
        }
    }
}
