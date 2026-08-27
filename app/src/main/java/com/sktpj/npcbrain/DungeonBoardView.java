package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class DungeonBoardView extends View {
    private static final int MAX_EFFECTS = 24;
    private static final long HIT_DURATION_MS = 420L;
    private static final long HURT_DURATION_MS = 460L;
    private static final long KILL_DURATION_MS = 520L;
    private static final long IMPACT_HOLD_MS = 65L;
    private static final long SHAKE_DURATION_MS = 190L;

    private static final class ImpactEffect {
        final DungeonCombatEvent event;
        final long startedAtMs;
        final int ordinal;

        ImpactEffect(DungeonCombatEvent event, long startedAtMs, int ordinal) {
            this.event = event;
            this.startedAtMs = startedAtMs;
            this.ordinal = ordinal;
        }

        long durationMs() {
            if (DungeonCombatEvent.ENEMY_DEFEATED.equals(event.type)) return KILL_DURATION_MS;
            if (DungeonCombatEvent.PLAYER_DAMAGED.equals(event.type)) return HURT_DURATION_MS;
            return HIT_DURATION_MS;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint();
    private final float density;
    private final List<ImpactEffect> effects = new ArrayList<>();
    private final Bitmap playerSpriteSheet;
    private final Rect playerSpriteSource = new Rect();
    private final RectF playerSpriteDestination = new RectF();
    private DungeonState state;
    private DungeonState lastStateRef;
    private int lastFloor = -1;
    private int lastPlayerX;
    private int lastPlayerY;
    private int walkFromX;
    private int walkFromY;
    private int walkToX;
    private int walkToY;
    private int walkDirectionRow = DungeonWalkAnimationPolicy.DOWN;
    private long walkStartedAtMs = -1L;

    DungeonBoardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.rgb(4, 8, 14));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        spritePaint.setAntiAlias(false);
        spritePaint.setDither(false);
        spritePaint.setFilterBitmap(false);
        playerSpriteSheet = DungeonPlayerSpriteSheet.decode(context);
    }

    void setState(DungeonState nextState) {
        if (nextState == null) {
            state = null;
            lastStateRef = null;
            lastFloor = -1;
            walkStartedAtMs = -1L;
            invalidate();
            return;
        }

        boolean hasPrevious = lastStateRef != null && lastFloor >= 0;
        boolean sameState = lastStateRef == nextState;
        boolean sameFloor = hasPrevious && lastFloor == nextState.floor;
        int dx = hasPrevious ? nextState.playerX - lastPlayerX : 0;
        int dy = hasPrevious ? nextState.playerY - lastPlayerY : 0;

        if (hasPrevious && DungeonWalkAnimationPolicy.isSingleStep(sameState, sameFloor, dx, dy)) {
            walkFromX = lastPlayerX;
            walkFromY = lastPlayerY;
            walkToX = nextState.playerX;
            walkToY = nextState.playerY;
            walkDirectionRow = DungeonWalkAnimationPolicy.directionRow(dx, dy, walkDirectionRow);
            walkStartedAtMs = SystemClock.uptimeMillis();
        } else if (!sameState || !sameFloor) {
            walkStartedAtMs = -1L;
        }

        state = nextState;
        lastStateRef = nextState;
        lastFloor = nextState.floor;
        lastPlayerX = nextState.playerX;
        lastPlayerY = nextState.playerY;
        invalidate();
    }

    void playCombatEvents(List<DungeonCombatEvent> events) {
        if (events == null || events.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        int ordinal = 0;
        for (DungeonCombatEvent event : events) {
            if (event == null) continue;
            if (DungeonCombatEvent.FLOOR_CHANGED.equals(event.type)) {
                clearEffects();
                continue;
            }
            if (!event.isCombatImpact()) continue;
            effects.add(new ImpactEffect(event, now, ordinal++));
        }
        while (effects.size() > MAX_EFFECTS) effects.remove(0);
        if (!effects.isEmpty()) postInvalidateOnAnimation();
    }

    void clearEffects() {
        effects.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null || state.width <= 0 || state.height <= 0) return;

        long now = SystemClock.uptimeMillis();
        pruneEffects(now);

        float minCell = 32f * density;
        float ideal = Math.min(getWidth() / 9f, getHeight() / 10.5f);
        float cell = Math.max(minCell, Math.min(52f * density, ideal));
        int cols = oddClamp((int) Math.floor(getWidth() / cell), 7, 13);
        int rows = oddClamp((int) Math.floor(getHeight() / cell), 7, 13);
        cell = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);

        int startX = clamp(state.playerX - cols / 2, 0, Math.max(0, state.width - cols));
        int startY = clamp(state.playerY - rows / 2, 0, Math.max(0, state.height - rows));
        int endX = Math.min(state.width, startX + cols);
        int endY = Math.min(state.height, startY + rows);
        float boardWidth = (endX - startX) * cell;
        float boardHeight = (endY - startY) * cell;
        float left = (getWidth() - boardWidth) / 2f;
        float top = (getHeight() - boardHeight) / 2f;

        float[] shake = shakeOffset(now);
        canvas.save();
        canvas.translate(shake[0], shake[1]);
        drawViewport(canvas, startX, startY, endX, endY, left, top, cell, now);
        drawImpactOverlays(canvas, startX, startY, endX, endY, left, top, cell, now);
        canvas.restore();

        drawMiniMap(canvas);
        drawFrame(canvas, left, top, boardWidth, boardHeight);
        if (!effects.isEmpty() || isWalkActive(now)) postInvalidateOnAnimation();
    }

    private void drawViewport(
            Canvas canvas,
            int startX,
            int startY,
            int endX,
            int endY,
            float left,
            float top,
            float cell,
            long now
    ) {
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                float l = left + (x - startX) * cell;
                float t = top + (y - startY) * cell;
                boolean explored = state.visited[y][x];
                boolean visible = DungeonPerception.isVisible(state, x, y);
                drawTile(canvas, x, y, l, t, cell, explored, visible);
            }
        }

        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !DungeonPerception.isVisible(state, enemy.x, enemy.y)) continue;
            if (enemy.x < startX || enemy.x >= endX || enemy.y < startY || enemy.y >= endY) continue;
            float cx = left + (enemy.x - startX + 0.5f) * cell;
            float cy = top + (enemy.y - startY + 0.5f) * cell;
            float[] recoil = enemyRecoil(enemy.x, enemy.y, now, cell);
            drawEnemy(canvas, cx + recoil[0], cy + recoil[1], cell, enemy.hp);
        }

        long walkElapsed = walkStartedAtMs < 0L ? -1L : now - walkStartedAtMs;
        boolean walking = DungeonWalkAnimationPolicy.isActive(walkElapsed);
        float playerGridX = state.playerX;
        float playerGridY = state.playerY;
        int frame = 0;
        if (walking) {
            float progress = DungeonWalkAnimationPolicy.progress(walkElapsed);
            playerGridX = walkFromX + (walkToX - walkFromX) * progress;
            playerGridY = walkFromY + (walkToY - walkFromY) * progress;
            frame = DungeonWalkAnimationPolicy.frameIndex(walkElapsed);
        }
        float playerCx = left + (playerGridX - startX + 0.5f) * cell;
        float playerCy = top + (playerGridY - startY + 0.5f) * cell;
        drawPlayer(canvas, playerCx, playerCy, cell, frame, walkDirectionRow);
    }

    private void drawTile(
            Canvas canvas,
            int x,
            int y,
            float left,
            float top,
            float cell,
            boolean explored,
            boolean visible
    ) {
        if (!explored) {
            paint.setColor(Color.rgb(5, 9, 15));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setStrokeWidth(Math.max(1f, cell * 0.02f));
            paint.setColor(Color.rgb(10, 17, 26));
            canvas.drawLine(left, top + cell, left + cell, top, paint);
            return;
        }

        int tile = state.tileAt(x, y);
        if (tile == DungeonState.WALL) {
            paint.setColor(visible ? Color.rgb(54, 68, 84) : Color.rgb(30, 39, 51));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setColor(visible ? Color.rgb(78, 96, 116) : Color.rgb(43, 54, 68));
            canvas.drawRect(left, top, left + cell, top + cell * 0.16f, paint);
            paint.setColor(Color.rgb(18, 27, 38));
            canvas.drawRect(left, top + cell * 0.84f, left + cell, top + cell, paint);
        } else {
            int base = ((x + y) & 1) == 0 ? 45 : 49;
            if (!visible) base -= 18;
            paint.setColor(Color.rgb(base, base + 5, base + 9));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setColor(visible ? Color.rgb(59, 68, 77) : Color.rgb(35, 42, 50));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, cell * 0.018f));
            canvas.drawRect(left + 1, top + 1, left + cell - 1, top + cell - 1, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        if (tile == DungeonState.STAIRS && (visible || explored)) {
            float inset = cell * 0.18f;
            paint.setColor(visible ? Color.rgb(213, 177, 82) : Color.rgb(116, 99, 59));
            canvas.drawRoundRect(new RectF(
                    left + inset, top + inset, left + cell - inset, top + cell - inset),
                    cell * 0.10f, cell * 0.10f, paint);
            textPaint.setColor(visible ? Color.rgb(30, 24, 13) : Color.rgb(220, 208, 163));
            textPaint.setTextSize(Math.max(12f * density, cell * 0.48f));
            canvas.drawText(">", left + cell / 2f, top + cell * 0.69f, textPaint);
        }
    }

    private void drawEnemy(Canvas canvas, float cx, float cy, float cell, int hp) {
        float r = cell * 0.30f;
        paint.setColor(Color.argb(70, 232, 79, 91));
        canvas.drawCircle(cx, cy, cell * 0.43f, paint);
        paint.setColor(Color.rgb(207, 70, 82));
        canvas.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r),
                cell * 0.10f, cell * 0.10f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, cell * 0.05f));
        paint.setColor(Color.rgb(255, 147, 147));
        canvas.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r),
                cell * 0.10f, cell * 0.10f, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(Math.max(10f * density, cell * 0.27f));
        textPaint.setColor(Color.WHITE);
        canvas.drawText("E" + hp, cx, cy + cell * 0.10f, textPaint);
    }

    private void drawPlayer(Canvas canvas, float cx, float cy, float cell, int frame, int row) {
        if (playerSpriteSheet != null) {
            int safeFrame = Math.max(0, Math.min(DungeonPlayerSpriteSheet.COLUMNS - 1, frame));
            int safeRow = Math.max(0, Math.min(DungeonPlayerSpriteSheet.ROWS - 1, row));
            int sourceLeft = safeFrame * DungeonPlayerSpriteSheet.CELL_SIZE;
            int sourceTop = safeRow * DungeonPlayerSpriteSheet.CELL_SIZE;
            playerSpriteSource.set(
                    sourceLeft,
                    sourceTop,
                    sourceLeft + DungeonPlayerSpriteSheet.CELL_SIZE,
                    sourceTop + DungeonPlayerSpriteSheet.CELL_SIZE);
            float side = cell * 0.96f;
            playerSpriteDestination.set(
                    cx - side / 2f,
                    cy - side / 2f,
                    cx + side / 2f,
                    cy + side / 2f);
            canvas.drawBitmap(playerSpriteSheet, playerSpriteSource, playerSpriteDestination, spritePaint);
            return;
        }
        drawPlayerFallback(canvas, cx, cy, cell);
    }

    private void drawPlayerFallback(Canvas canvas, float cx, float cy, float cell) {
        paint.setColor(Color.argb(62, 76, 170, 255));
        canvas.drawCircle(cx, cy, cell * 0.49f, paint);
        paint.setColor(Color.rgb(76, 169, 246));
        canvas.drawCircle(cx, cy, cell * 0.34f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, cell * 0.055f));
        paint.setColor(Color.rgb(196, 233, 255));
        canvas.drawCircle(cx, cy, cell * 0.34f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(231, 249, 255));
        canvas.drawCircle(cx, cy, cell * 0.11f, paint);
    }

    private boolean isWalkActive(long now) {
        if (walkStartedAtMs < 0L) return false;
        return DungeonWalkAnimationPolicy.isActive(now - walkStartedAtMs);
    }

    private void drawImpactOverlays(
            Canvas canvas,
            int startX,
            int startY,
            int endX,
            int endY,
            float left,
            float top,
            float cell,
            long now
    ) {
        boolean playerHurt = false;
        float hurtAlpha = 0f;
        for (ImpactEffect effect : effects) {
            DungeonCombatEvent event = effect.event;
            long age = now - effect.startedAtMs;
            if (age < 0L || age > effect.durationMs()) continue;
            float p = effectProgress(effect, age);
            if (DungeonCombatEvent.PLAYER_HIT.equals(event.type)) {
                drawPlayerHitEffect(canvas, effect, startX, startY, endX, endY,
                        left, top, cell, age, p);
            } else if (DungeonCombatEvent.ENEMY_DEFEATED.equals(event.type)) {
                drawDefeatEffect(canvas, effect, startX, startY, endX, endY,
                        left, top, cell, p);
            } else if (DungeonCombatEvent.PLAYER_DAMAGED.equals(event.type)) {
                drawPlayerDamageEffect(canvas, effect, startX, startY, endX, endY,
                        left, top, cell, age, p);
                playerHurt = true;
                hurtAlpha = Math.max(hurtAlpha, 1f - Math.min(1f, age / 260f));
            }
        }
        if (playerHurt && hurtAlpha > 0f) {
            drawDamageVignette(canvas, left, top,
                    (endX - startX) * cell, (endY - startY) * cell, hurtAlpha);
        }
    }

    private void drawPlayerHitEffect(
            Canvas canvas,
            ImpactEffect effect,
            int startX,
            int startY,
            int endX,
            int endY,
            float left,
            float top,
            float cell,
            long age,
            float p
    ) {
        DungeonCombatEvent event = effect.event;
        if (!insideViewport(event.targetX, event.targetY, startX, startY, endX, endY)) return;
        float tx = cellCenterX(event.targetX, startX, left, cell);
        float ty = cellCenterY(event.targetY, startY, top, cell);
        float sx = cellCenterX(event.sourceX, startX, left, cell);
        float sy = cellCenterY(event.sourceY, startY, top, cell);

        float flash = 1f - Math.min(1f, age / 145f);
        if (flash > 0f) {
            paint.setColor(Color.argb((int) (220f * flash), 255, 244, 205));
            canvas.drawCircle(tx, ty, cell * (0.37f + 0.07f * flash), paint);
        }

        float dx = tx - sx;
        float dy = ty - sy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) len = 1f;
        float nx = dx / len;
        float ny = dy / len;
        float px = -ny;
        float py = nx;
        float slashFade = 1f - Math.min(1f, p * 1.35f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(3f * density, cell * 0.095f));
        paint.setColor(Color.argb((int) (245f * slashFade), 255, 236, 168));
        canvas.drawLine(tx - px * cell * 0.38f - nx * cell * 0.08f,
                ty - py * cell * 0.38f - ny * cell * 0.08f,
                tx + px * cell * 0.38f + nx * cell * 0.08f,
                ty + py * cell * 0.38f + ny * cell * 0.08f, paint);
        paint.setStrokeWidth(Math.max(1.5f * density, cell * 0.035f));
        paint.setColor(Color.argb((int) (255f * slashFade), 255, 255, 248));
        canvas.drawLine(tx - px * cell * 0.31f, ty - py * cell * 0.31f,
                tx + px * cell * 0.31f, ty + py * cell * 0.31f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);

        drawFragments(canvas, effect, tx, ty, cell, p, false);
        drawDamagePopup(canvas, effect, tx, ty, cell, p, false);
    }

    private void drawDefeatEffect(
            Canvas canvas,
            ImpactEffect effect,
            int startX,
            int startY,
            int endX,
            int endY,
            float left,
            float top,
            float cell,
            float p
    ) {
        DungeonCombatEvent event = effect.event;
        if (!insideViewport(event.targetX, event.targetY, startX, startY, endX, endY)) return;
        float tx = cellCenterX(event.targetX, startX, left, cell);
        float ty = cellCenterY(event.targetY, startY, top, cell);
        float fade = 1f - p;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f * density, cell * 0.055f));
        paint.setColor(Color.argb((int) (230f * fade), 255, 202, 91));
        canvas.drawCircle(tx, ty, cell * (0.20f + 0.54f * p), paint);
        paint.setStyle(Paint.Style.FILL);
        drawFragments(canvas, effect, tx, ty, cell, p, true);
    }

    private void drawPlayerDamageEffect(
            Canvas canvas,
            ImpactEffect effect,
            int startX,
            int startY,
            int endX,
            int endY,
            float left,
            float top,
            float cell,
            long age,
            float p
    ) {
        DungeonCombatEvent event = effect.event;
        if (!insideViewport(event.targetX, event.targetY, startX, startY, endX, endY)) return;
        float tx = cellCenterX(event.targetX, startX, left, cell);
        float ty = cellCenterY(event.targetY, startY, top, cell);
        float flash = 1f - Math.min(1f, age / 160f);
        if (flash > 0f) {
            paint.setColor(Color.argb((int) (205f * flash), 255, 90, 102));
            canvas.drawCircle(tx, ty, cell * 0.48f, paint);
            paint.setColor(Color.argb((int) (150f * flash), 255, 235, 235));
            canvas.drawCircle(tx, ty, cell * 0.28f, paint);
        }
        drawDamagePopup(canvas, effect, tx, ty, cell, p, true);
        drawFragments(canvas, effect, tx, ty, cell, p, true);
    }

    private void drawDamagePopup(
            Canvas canvas,
            ImpactEffect effect,
            float tx,
            float ty,
            float cell,
            float p,
            boolean hurt
    ) {
        if (effect.event.damage <= 0) return;
        float rise = cell * (0.12f + 0.54f * p);
        float spread = ((effect.ordinal % 3) - 1) * cell * 0.16f;
        float fade = 1f - Math.max(0f, (p - 0.58f) / 0.42f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(Math.max(15f * density, cell * 0.38f));
        textPaint.setShadowLayer(3f * density, 0f, 1f * density, Color.argb(190, 0, 0, 0));
        textPaint.setColor(hurt
                ? Color.argb((int) (255f * fade), 255, 113, 122)
                : Color.argb((int) (255f * fade), 255, 231, 144));
        canvas.drawText("-" + effect.event.damage, tx + spread, ty - rise, textPaint);
        textPaint.clearShadowLayer();
    }

    private void drawFragments(
            Canvas canvas,
            ImpactEffect effect,
            float tx,
            float ty,
            float cell,
            float p,
            boolean strong
    ) {
        int count = strong ? 8 : 6;
        float fade = 1f - p;
        int seed = effect.event.targetX * 31
                + effect.event.targetY * 17
                + effect.ordinal * 13
                + effect.event.type.hashCode();
        float phase = ((seed & 255) / 255f) * (float) (Math.PI * 2.0);
        for (int i = 0; i < count; i++) {
            float angle = phase + (float) (Math.PI * 2.0 * i / count);
            float distance = cell * ((strong ? 0.14f : 0.10f) + (strong ? 0.56f : 0.42f) * p);
            float x = tx + (float) Math.cos(angle) * distance;
            float y = ty + (float) Math.sin(angle) * distance;
            float radius = cell * (strong ? 0.065f : 0.045f) * (0.55f + 0.45f * fade);
            paint.setColor(strong
                    ? Color.argb((int) (230f * fade), 255, 116, 92)
                    : Color.argb((int) (235f * fade), 255, 212, 108));
            canvas.drawCircle(x, y, Math.max(1.5f * density, radius), paint);
        }
    }

    private void drawDamageVignette(
            Canvas canvas,
            float left,
            float top,
            float width,
            float height,
            float strength
    ) {
        float alpha = Math.min(1f, strength);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9f * density);
        paint.setColor(Color.argb((int) (155f * alpha), 220, 44, 60));
        float inset = 5f * density;
        canvas.drawRoundRect(new RectF(
                        left + inset, top + inset, left + width - inset, top + height - inset),
                12f * density, 12f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float[] enemyRecoil(int x, int y, long now, float cell) {
        float ox = 0f;
        float oy = 0f;
        for (ImpactEffect effect : effects) {
            if (!DungeonCombatEvent.PLAYER_HIT.equals(effect.event.type)) continue;
            if (effect.event.targetX != x || effect.event.targetY != y) continue;
            long age = now - effect.startedAtMs;
            if (age < 0L || age > 150L) continue;
            float t = age / 150f;
            float recoil = (float) Math.sin(Math.PI * t) * cell * 0.10f;
            int dx = effect.event.targetX - effect.event.sourceX;
            int dy = effect.event.targetY - effect.event.sourceY;
            ox += dx * recoil;
            oy += dy * recoil;
        }
        return new float[]{ox, oy};
    }

    private float[] shakeOffset(long now) {
        float amplitudeDp = 0f;
        long newestAge = Long.MAX_VALUE;
        for (ImpactEffect effect : effects) {
            long age = now - effect.startedAtMs;
            if (age < 0L || age > SHAKE_DURATION_MS) continue;
            float candidate;
            if (DungeonCombatEvent.PLAYER_DAMAGED.equals(effect.event.type)) candidate = 4.5f;
            else if (DungeonCombatEvent.ENEMY_DEFEATED.equals(effect.event.type)) candidate = 3.6f;
            else if (DungeonCombatEvent.PLAYER_HIT.equals(effect.event.type)) candidate = 2.2f;
            else continue;
            amplitudeDp = Math.max(amplitudeDp, candidate);
            newestAge = Math.min(newestAge, age);
        }
        if (amplitudeDp <= 0f || newestAge == Long.MAX_VALUE) return new float[]{0f, 0f};
        float t = Math.min(1f, newestAge / (float) SHAKE_DURATION_MS);
        float decay = (1f - t) * (1f - t);
        float amplitude = amplitudeDp * density * decay;
        float x = (float) Math.sin(newestAge * 0.23) * amplitude;
        float y = (float) Math.cos(newestAge * 0.31) * amplitude * 0.72f;
        return new float[]{x, y};
    }

    private float effectProgress(ImpactEffect effect, long age) {
        if (age <= IMPACT_HOLD_MS) return 0f;
        long effective = Math.max(1L, effect.durationMs() - IMPACT_HOLD_MS);
        return Math.min(1f, (age - IMPACT_HOLD_MS) / (float) effective);
    }

    private void pruneEffects(long now) {
        Iterator<ImpactEffect> iterator = effects.iterator();
        while (iterator.hasNext()) {
            ImpactEffect effect = iterator.next();
            if (now - effect.startedAtMs > effect.durationMs()) iterator.remove();
        }
    }

    private static boolean insideViewport(
            int x,
            int y,
            int startX,
            int startY,
            int endX,
            int endY
    ) {
        return x >= startX && x < endX && y >= startY && y < endY;
    }

    private static float cellCenterX(int x, int startX, float left, float cell) {
        return left + (x - startX + 0.5f) * cell;
    }

    private static float cellCenterY(int y, int startY, float top, float cell) {
        return top + (y - startY + 0.5f) * cell;
    }

    private void drawMiniMap(Canvas canvas) {
        float size = Math.min(112f * density, Math.min(getWidth(), getHeight()) * 0.29f);
        float margin = 10f * density;
        float left = getWidth() - size - margin;
        float top = margin;
        paint.setColor(Color.argb(214, 7, 13, 22));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size),
                10f * density, 10f * density, paint);

        float inset = 8f * density;
        float cell = Math.min((size - inset * 2f) / state.width,
                (size - inset * 2f) / state.height);
        float mapLeft = left + (size - cell * state.width) / 2f;
        float mapTop = top + (size - cell * state.height) / 2f;
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                if (!state.visited[y][x]) continue;
                int tile = state.tileAt(x, y);
                if (tile == DungeonState.WALL) paint.setColor(Color.rgb(67, 81, 96));
                else if (tile == DungeonState.STAIRS) paint.setColor(Color.rgb(223, 183, 84));
                else paint.setColor(Color.rgb(126, 141, 154));
                canvas.drawRect(mapLeft + x * cell, mapTop + y * cell,
                        mapLeft + (x + 1) * cell, mapTop + (y + 1) * cell, paint);
            }
        }
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !DungeonPerception.isVisible(state, enemy.x, enemy.y)) continue;
            paint.setColor(Color.rgb(230, 81, 91));
            canvas.drawCircle(mapLeft + (enemy.x + 0.5f) * cell,
                    mapTop + (enemy.y + 0.5f) * cell,
                    Math.max(2f * density, cell * 0.65f), paint);
        }
        paint.setColor(Color.rgb(83, 185, 255));
        canvas.drawCircle(mapLeft + (state.playerX + 0.5f) * cell,
                mapTop + (state.playerY + 0.5f) * cell,
                Math.max(2.5f * density, cell * 0.85f), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.rgb(94, 119, 147));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size),
                10f * density, 10f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFrame(Canvas canvas, float left, float top, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(Color.rgb(45, 65, 88));
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                10f * density, 10f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static int oddClamp(int value, int min, int max) {
        int result = Math.max(min, Math.min(max, value));
        if ((result & 1) == 0) result--;
        return Math.max(min, result);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
