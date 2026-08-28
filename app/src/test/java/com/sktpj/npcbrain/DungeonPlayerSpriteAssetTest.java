package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DungeonPlayerSpriteAssetTest {
    private static final String EXPECTED_SHA256 =
            "9c5b6659dbefc6867eb556a0a67c799bcdbebc2000cc874bf4d93b75b178c177";

    @Test
    public void spriteSheetIsCompleteExpectedFourDirectionAsset() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src/main/assets/dungeon_adventurer_walk_4x4.png"));
        assertTrue(bytes.length > 1000);

        assertArrayEquals(
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                java.util.Arrays.copyOfRange(bytes, 0, 8));

        ByteBuffer ihdr = ByteBuffer.wrap(bytes, 16, 8).order(ByteOrder.BIG_ENDIAN);
        assertEquals(64, ihdr.getInt());
        assertEquals(64, ihdr.getInt());

        byte[] iendType = java.util.Arrays.copyOfRange(bytes, bytes.length - 8, bytes.length - 4);
        assertArrayEquals(new byte[]{0x49, 0x45, 0x4e, 0x44}, iendType);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        assertEquals(EXPECTED_SHA256, toHex(digest.digest(bytes)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }
}
