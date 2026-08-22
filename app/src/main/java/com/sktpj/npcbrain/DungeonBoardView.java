package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

final class DungeonBoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DungeonState state;

    DungeonBoardView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(5, 9, 16));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setState(DungeonState state) {
        this.state = state;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null || state.width <= 0 || state.height <= 0) return;

        float cell = Math.min(
                getWidth() / (float) state.width,
                getHeight() / (float) state.height);
        float boardWidth = cell * state.width;
        float boardHeight = cell * state.height;
        float left = (getWidth() - boardWidth) / 2f;
        float top = (getHeight() - boardHeight) / 2f;

        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                int tile = state.tiles[y][x];
                if (tile == DungeonState.WALL) {
                    paint.setColor(Color.rgb(30, 38, 50));
                } else if (tile == DungeonState.STAIRS) {
                    paint.setColor(Color.rgb(74, 90, 62));
                } else if (state.visited[y][x]) {
                    paint.setColor(Color.rgb(54, 61, 69));
                } else {
                    paint.setColor(Color.rgb(42, 47, 55));
                }
                float l = left + x * cell;
                float t = top + y * cell;
                canvas.drawRect(l, t, l + cell, t + cell, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, cell * 0.025f));
                paint.setColor(Color.rgb(18, 24, 33));
                canvas.drawRect(l, t, l + cell, t + cell, paint);
                paint.setStyle(Paint.Style.FILL);

                if (tile == DungeonState.STAIRS) {
                    textPaint.setColor(Color.rgb(227, 235, 184));
                    textPaint.setTextSize(Math.max(10f, cell * 0.7f));
                    canvas.drawText(">", l + cell / 2f, t + cell * 0.73f, textPaint);
                }
            }
        }

        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive()) continue;
            float cx = left + (enemy.x + 0.5f) * cell;
            float cy = top + (enemy.y + 0.5f) * cell;
            float radius = cell * 0.28f;
            paint.setColor(Color.rgb(190, 71, 71));
            canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, paint);
            textPaint.setTextSize(Math.max(8f, cell * 0.3f));
            textPaint.setColor(Color.WHITE);
            canvas.drawText(Integer.toString(enemy.hp), cx, cy + cell * 0.11f, textPaint);
        }

        float playerCx = left + (state.playerX + 0.5f) * cell;
        float playerCy = top + (state.playerY + 0.5f) * cell;
        paint.setColor(Color.rgb(91, 164, 235));
        canvas.drawCircle(playerCx, playerCy, cell * 0.34f, paint);
        paint.setColor(Color.rgb(228, 245, 255));
        canvas.drawCircle(playerCx, playerCy, cell * 0.12f, paint);
    }
}
