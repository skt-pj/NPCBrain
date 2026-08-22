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
        DungeonIntent intent = DungeonIntent.localFallback(state, traits, "Brain未更新");
        return stepDetailed(state, traits, intent).state;
    }

    static DungeonState step(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonIntent intent
    ) {
        return stepDetailed(state, traits, intent).state;
    }

    static DungeonState step(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonPersonalityPolicy.Direction direction
    ) {
        return stepDetailed(state, traits, direction).state;
    }

    static DungeonStepResult stepDetailed(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonIntent intent
    ) {
        DungeonPersonalityPolicy.Direction direction = DungeonPersonalityPolicy.choose(
                state, traits, intent);
        return stepDetailed(state, traits, direction);
    }

    static DungeonStepResult stepDetailed(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonPersonalityPolicy.Direction direction
    ) {
        List<DungeonCombatEvent> events = new ArrayList<>();
        if (state == null) {
            DungeonState generated = DungeonGenerator.generate(System.nanoTime(), 1);
            DungeonPerception.refreshExploration(generated);
            return new DungeonStepResult(generated, events);
        }
        if (state.hp <= 0) {
            DungeonState restarted = DungeonGenerator.generate(
                    DungeonGenerator.nextFloorSeed(state.seed, 1),
                    1,
                    state.maxHp,
                    state.maxHp,
                    state.turn + 1);
            DungeonPerception.refreshExploration(restarted);
            restarted.lastAction = "倒れたため1Fから再開";
            return new DungeonStepResult(restarted, events);
        }

        int floorBefore = state.floor;
        int playerBeforeX = state.playerX;
        int playerBeforeY = state.playerY;
        String playerAction = resolvePlayerAction(state, direction, events);
        DungeonPerception.refreshExploration(state);

        if (state.tileAt(state.playerX, state.playerY) == DungeonState.STAIRS) {
            int nextFloor = state.floor + 1;
            DungeonState next = DungeonGenerator.generate(
                    DungeonGenerator.nextFloorSeed(state.seed, nextFloor),
                    nextFloor,
                    state.maxHp,
                    state.maxHp,
                    state.turn + 1);
            DungeonPerception.refreshExploration(next);
            next.lastAction = state.floor + "Fクリア → " + nextFloor + "Fへ";
            events.add(new DungeonCombatEvent(
                    DungeonCombatEvent.FLOOR_CHANGED,
                    playerBeforeX,
                    playerBeforeY,
                    state.playerX,
                    state.playerY,
                    0,
                    Integer.toString(floorBefore)));
            return new DungeonStepResult(next, events);
        }

        int damageTaken = resolveEnemyPhase(state, events);
        state.turn++;
        DungeonPerception.refreshExploration(state);
        StringBuilder action = new StringBuilder(playerAction);
        if (damageTaken > 0) action.append(" / ").append(damageTaken).append("ダメージ");
        if (state.hp <= 0) action.append(" / 倒れた");
        state.lastAction = action.toString();
        return new DungeonStepResult(state, events);
    }

    private static String resolvePlayerAction(
            DungeonState state,
            DungeonPersonalityPolicy.Direction direction,
            List<DungeonCombatEvent> events
    ) {
        if (direction == null || direction == DungeonPersonalityPolicy.Direction.WAIT) {
            return "周囲を警戒";
        }
        int sourceX = state.playerX;
        int sourceY = state.playerY;
        int targetX = state.playerX + direction.dx;
        int targetY = state.playerY + direction.dy;
        if (!state.walkable(targetX, targetY)) return "壁を警戒";

        DungeonState.Enemy enemy = state.enemyAt(targetX, targetY);
        if (enemy != null) {
            int hpBefore = enemy.hp;
            if (playerAttack(state, enemy)) {
                int appliedDamage = Math.max(0, hpBefore - enemy.hp);
                if (appliedDamage > 0) {
                    events.add(new DungeonCombatEvent(
                            DungeonCombatEvent.PLAYER_HIT,
                            sourceX,
                            sourceY,
                            enemy.x,
                            enemy.y,
                            appliedDamage,
                            enemy.id));
                    if (!enemy.alive()) {
                        events.add(new DungeonCombatEvent(
                                DungeonCombatEvent.ENEMY_DEFEATED,
                                sourceX,
                                sourceY,
                                enemy.x,
                                enemy.y,
                                appliedDamage,
                                enemy.id));
                    }
                }
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

    private static int resolveEnemyPhase(
            DungeonState state,
            List<DungeonCombatEvent> events
    ) {
        int damageTaken = 0;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || state.hp <= 0) continue;
            if (canAttack(enemy.x, enemy.y, state.playerX, state.playerY)) {
                int hpBefore = state.hp;
                state.hp = Math.max(0, state.hp - ENEMY_DAMAGE);
                int appliedDamage = Math.max(0, hpBefore - state.hp);
                damageTaken += appliedDamage;
                if (appliedDamage > 0) {
                    events.add(new DungeonCombatEvent(
                            DungeonCombatEvent.PLAYER_DAMAGED,
                            enemy.x,
                            enemy.y,
                            state.playerX,
                            state.playerY,
                            appliedDamage,
                            "player"));
                }
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
