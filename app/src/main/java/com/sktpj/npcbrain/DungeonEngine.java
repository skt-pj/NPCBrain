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
            DungeonIntent intent,
            DungeonPlan plan
    ) {
        return stepDetailed(state, traits, intent, plan).state;
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
        return stepDetailed(state, traits, intent, null);
    }

    static DungeonStepResult stepDetailed(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonIntent intent,
            DungeonPlan plan
    ) {
        DungeonStore.refreshSharedWorldForTurn(state);
        DungeonPersonalityPolicy.Direction direction = legalBrainDirection(state, intent);
        if (direction == null) {
            direction = DungeonPersonalityPolicy.choose(state, traits, intent, plan);
        }
        return stepDetailedInternal(state, traits, direction, false);
    }

    /**
     * A same-cycle Brain action is authoritative when it is still physically feasible.
     * The local compiler may replace it only when the world state makes that exact action illegal.
     */
    static DungeonPersonalityPolicy.Direction legalBrainDirection(
            DungeonState state,
            DungeonIntent intent
    ) {
        if (state == null || intent == null || !intent.isBrain()) return null;
        DungeonPersonalityPolicy.Direction direction = intent.preferredDirection;
        if (direction == null) return null;
        if (direction == DungeonPersonalityPolicy.Direction.WAIT) {
            return direction;
        }
        int targetX = state.playerX + direction.dx;
        int targetY = state.playerY + direction.dy;
        if (!state.walkable(targetX, targetY)) return null;
        DungeonState.Enemy enemyAtTarget = state.enemyAt(targetX, targetY);
        if (enemyAtTarget == null && DungeonTurnContext.occupiedByPeer(state, targetX, targetY)) {
            return null;
        }
        if (!intent.targetId.isEmpty()) {
            DungeonState.Enemy target = enemyAtTarget;
            if (target == null || !target.alive() || !intent.targetId.equals(target.id)) return null;
            if (!DungeonPerception.isVisible(state, target.x, target.y)) return null;
        }
        return direction;
    }

    static DungeonStepResult stepDetailed(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonPersonalityPolicy.Direction direction
    ) {
        return stepDetailedInternal(state, traits, direction, true);
    }

    private static DungeonStepResult stepDetailedInternal(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonPersonalityPolicy.Direction direction,
            boolean refreshWorld
    ) {
        List<DungeonCombatEvent> events = new ArrayList<>();
        if (state == null) {
            DungeonState generated = DungeonGenerator.generate(System.nanoTime(), 1);
            DungeonPerception.refreshExploration(generated);
            return new DungeonStepResult(generated, events);
        }
        if (refreshWorld) DungeonStore.refreshSharedWorldForTurn(state);
        if (state.hp <= 0) {
            state.hp = 0;
            if (state.lastAction == null || !state.lastAction.contains("死亡")) {
                state.lastAction = "死亡";
            }
            return new DungeonStepResult(state, events);
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
        if (state.hp <= 0) action.append(" / 死亡");
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
        if (DungeonTurnContext.occupiedByPeer(state, targetX, targetY)) {
            return "他の冒険者がいるため待機";
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
        int damageTakenByCurrent = 0;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive()) continue;
            DungeonActorContext target = DungeonTurnContext.nearestLivingTarget(
                    state, enemy.x, enemy.y);
            if (target == null || !target.alive()) continue;
            if (canAttack(enemy.x, enemy.y, target.x, target.y)) {
                int hpBefore = target.hp;
                DungeonTurnContext.applyDamage(state, target, ENEMY_DAMAGE);
                DungeonTurnContext.Snapshot metadata = DungeonTurnContext.lookup(state);
                boolean currentTarget = metadata != null
                        && metadata.ownerNpcId.equals(target.npcId);
                int hpAfter = currentTarget ? state.hp : peerHp(state, target.npcId, hpBefore);
                int appliedDamage = Math.max(0, hpBefore - hpAfter);
                if (currentTarget) damageTakenByCurrent += appliedDamage;
                if (appliedDamage > 0) {
                    events.add(new DungeonCombatEvent(
                            DungeonCombatEvent.PLAYER_DAMAGED,
                            enemy.x,
                            enemy.y,
                            target.x,
                            target.y,
                            appliedDamage,
                            target.npcId));
                }
                continue;
            }
            moveEnemyOneStep(state, enemy, target);
        }
        return damageTakenByCurrent;
    }

    private static int peerHp(DungeonState state, String npcId, int fallback) {
        DungeonTurnContext.Snapshot metadata = DungeonTurnContext.lookup(state);
        if (metadata == null) return fallback;
        for (DungeonActorContext peer : metadata.peers) {
            if (peer.npcId.equals(npcId)) return peer.hp;
        }
        return fallback;
    }

    private static void moveEnemyOneStep(
            DungeonState state,
            DungeonState.Enemy enemy,
            DungeonActorContext target
    ) {
        List<int[]> candidates = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int nx = enemy.x + dir[0];
            int ny = enemy.y + dir[1];
            if (!state.walkable(nx, ny)) continue;
            if (state.tileAt(nx, ny) == DungeonState.STAIRS) continue;
            if (DungeonTurnContext.occupiedByAnyActorExcept(state, "", nx, ny)) continue;
            if (occupiedByOtherEnemy(state, enemy, nx, ny)) continue;
            candidates.add(new int[]{nx, ny});
        }
        candidates.sort(Comparator.comparingInt(point ->
                Math.abs(point[0] - target.x) + Math.abs(point[1] - target.y)));
        if (!candidates.isEmpty()) {
            int[] best = candidates.get(0);
            int oldDistance = Math.abs(enemy.x - target.x) + Math.abs(enemy.y - target.y);
            int newDistance = Math.abs(best[0] - target.x) + Math.abs(best[1] - target.y);
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
