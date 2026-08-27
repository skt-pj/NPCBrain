package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

final class DungeonPlayerSpriteSheet {
    static final int ROWS = 4;
    static final int COLUMNS = 4;
    static final int CELL_SIZE = 64;
    static final int SHEET_SIZE = CELL_SIZE * COLUMNS;

    private DungeonPlayerSpriteSheet() {
    }

    static Bitmap decode(Context context) {
        if (context == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(
                    context.getResources(),
                    R.drawable.dungeon_adventurer_walk_4x4,
                    options);
            if (bitmap == null
                    || bitmap.getWidth() != SHEET_SIZE
                    || bitmap.getHeight() != CELL_SIZE * ROWS) {
                if (bitmap != null) bitmap.recycle();
                return null;
            }
            return bitmap;
        } catch (RuntimeException error) {
            return null;
        }
    }
}
