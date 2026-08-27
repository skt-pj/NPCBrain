package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;

final class DungeonPlayerSpriteSheet {
    static final int ROWS = 4;
    static final int COLUMNS = 4;
    static final int CELL_SIZE = 32;
    static final int SHEET_SIZE = CELL_SIZE * COLUMNS;
    static final String ASSET_NAME = "dungeon_adventurer_walk_4x4.png";

    private DungeonPlayerSpriteSheet() {
    }

    static Bitmap decode(Context context) {
        if (context == null) return null;
        try (InputStream stream = context.getAssets().open(ASSET_NAME)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap == null
                    || bitmap.getWidth() != SHEET_SIZE
                    || bitmap.getHeight() != CELL_SIZE * ROWS) {
                if (bitmap != null) bitmap.recycle();
                return null;
            }
            return bitmap;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }
}
