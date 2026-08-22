package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class DungeonEngine {
    private static final int PLAYER_DAMAGE = 2;
    private static final int ENEMY_DAMAGE = 1;

    private DungeonEngine() {
    }

    static DungeonState step(DungeonState state, DungeonPersonalityPolicy.Traits traits) {
        DungeonPersonalityPolicy.Direction direction = DungeonPersonalityPolicy.choose(state, traits);
        return step(state, traits, direction);
    }

    static DungeonState step(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonPersonalityPolicy.Direction direction
    ) {
        if (state == null) return DungeonGenerator.generate(System.nanoTime(), 1);
        if (state.hp <= 0) {
            DungeonState restarted = DungeonGenerator.generate(
                    DungeonGenerator.nextFloorSeed(state.seed, 1),
                    1,
                    state.maxHp,
                    state.maxHp,
                    state.turn + 1);
            restarted.lastAction = "倒れたため1Fから再開";
            return restarted;
        }

        String playerAction = resolvePlayerAction(state, direction);
        state.markVisited(state.playerX, state.playerY);

        if (state.tileAt(state.playerX, state.playerY) == DungeonState.STAIRS) {
            int nextFloor = state.floor + 1;
            DungeonState next = DungeonGenerator.generate(
                    DungeonGenerator.nextFloorSeed(state.seed, nextFloor),
                    nextFloor,
                    state.maxHp,
                    state.maxHp,
                    state.turn + 1);
            next.lastAction = state.floor + "Fクリア → " + nextFloor + "Fへ";
            return next;
        }

        int damageTaken = resolveEnemyPhase(state);
        state.turn++;
        StringBuilder action = new StringBuilder(playerAction);
        if (damageTaken > 0) action.append(" / ").append(damageTaken).append("ダメージ");
        if (state.hp <= 0) action.append(" / 倒れた");
        state.lastAction = action.toString();
        return state;
    }

    private static String resolvePlayerAction(
            DungeonState state,
            DungeonPersonalityPolicy.Direction direction
    ) {
        if (direction == null || direction == DungeonPersonalityPolicy.Direction.WAIT) {
            return "周囲を警戒";
        }
        int targetX = state.playerX + direction.dx;
        int targetY = state.playerY + direction.dy;
        if (!state.walkable(targetX, targetY)) return "壁を警戒";

        DungeonState.Enemy enemy = state.enemyAt(targetX, targetY);
        if (enemy != null) {
            if (playerAttack(state, enemy)) {
                return enemy.alive() ? "敵を攻撃" : "敵を倒した";
            }
            return "攻撃できない距離";
        }
        state.playerX = targetX;
        state.playerY = targetY;
        return state.tileAt(targetX, targetY) == DungeonState.STAIRS ? "階段へ進む" : "1マス移動";
    }

    static boolean canAttack(int attackerX, int attackerY, int targetX, int targetY) {
        return Math.abs(attackerX - targetX) + Math.abs(attackerY - targetY) == 1;
    }

    static boolean playerAttack(DungeonState state, DungeonState.Enemy enemy) {
        if (state == null || enemy == null || !enemy.alive()) return false;
        if (!canAttack(state.playerX, state.playerY, enemy.x, enemy.y)) return false;
        enemy.hp = Math.max(0, enemy.hp - PLAYER_DAMAGE);
        return true;
    }

    private static int resolveEnemyPhase(DungeonState state) {
        int damageTaken = 0;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || state.hp <= 0) continue;
            if (canAttack(enemy.x, enemy.y, state.playerX, state.playerY)) {
                state.hp = Math.max(0, state.hp - ENEMY_DAMAGE);
                damageTaken += ENEMY_DAMAGE;
                continue;
            }
            moveEnemyOneStep(state, enemy);
        }
        return damageTaken;
    }

    private static void moveEnemyOneStep(DungeonState state, DungeonState.Enemy enemy) {
        List<int[]> candidates = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int nx = enemy.x + dir[0];
            int ny = enemy.y + dir[1];
            if (!state.walkable(nx, ny)) continue;
            if (state.tileAt(nx, ny) == DungeonState.STAIRS) continue;
            if (nx == state.playerX && ny == state.playerY) continue;
            if (occupiedByOtherEnemy(state, enemy, nx, ny)) continue;
            candidates.add(new int[]{nx, ny});
        }
        candidates.sort(Comparator.comparingInt(point ->
                Math.abs(point[0] - state.playerX) + Math.abs(point[1] - state.playerY)));
        if (!candidates.isEmpty()) {
            int[] best = candidates.get(0);
            int oldDistance = Math.abs(enemy.x - state.playerX) + Math.abs(enemy.y - state.playerY);
            int newDistance = Math.abs(best[0] - state.playerX) + Math.abs(best[1] - state.playerY);
            if (newDistance < oldDistance) {
                enemy.x = best[0];
                enemy.y = best[1];
            }
        }
    }

    private static boolean occupiedByOtherEnemy(
            DungeonState state,
            DungeonState.Enemy moving,
            int x,
            int y
    ) {
        for (DungeonState.Enemy enemy : state.enemies) {
            if (enemy == moving || !enemy.alive()) continue;
            if (enemy.x == x && enemy.y == y) return true;
        }
        return false;
    }
}
