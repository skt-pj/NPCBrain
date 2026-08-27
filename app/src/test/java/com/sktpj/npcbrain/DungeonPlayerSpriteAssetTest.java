package com.sktpj.npcbrain;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DungeonPlayerSpriteAssetTest {
    @Test
    public void spriteSheetIsCompleteAndContainsFourDistinctDirectionRows() throws Exception {
        File file = new File("src/main/assets/dungeon_adventurer_walk_4x4.png");
        assertTrue(file.isFile());

        BufferedImage image = ImageIO.read(file);
        assertNotNull(image);
        assertEquals(128, image.getWidth());
        assertEquals(128, image.getHeight());

        Set<Long> rowHashes = new HashSet<>();
        for (int row = 0; row < 4; row++) {
            long hash = 1469598103934665603L;
            int top = row * 32;
            for (int y = top; y < top + 32; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    hash ^= image.getRGB(x, y);
                    hash *= 1099511628211L;
                }
            }
            rowHashes.add(hash);
        }
        assertEquals(4, rowHashes.size());
    }
}
