package com.sktpj.npcbrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

final class DungeonPersonalityPolicy {
    private static final int NORMAL_EVADE_LEASE_TURNS = 2;

    enum Direction {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0),
        WAIT(0, 0);

        final int dx;
        final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    static final class Traits {
        final int extraversion;
        final int neuroticism;
        final int agreeableness;
        final int conscientiousness;
        final int openness;

        Traits(
                int extraversion,
                int neuroticism,
                int agreeableness,
                int conscientiousness,
                int openness
        ) {
            this.extraversion = clamp(extraversion);
            this.neuroticism = clamp(neuroticism);
            this.agreeableness = clamp(agreeableness);
            this.conscientiousness = clamp(conscientiousness);
            this.openness = clamp(openness);
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }

    private DungeonPersonalityPolicy() {
    }

    static Direction choose(DungeonState state, Traits traits) {
        return choose(state, traits, DungeonIntent.localFallback(state, traits, "人格ベース"), null);
    }

    static Direction choose(DungeonState state, Traits traits, DungeonIntent intent) {
        return choose(state, traits, intent, null);
    }

    static Direction choose(
            DungeonState state,
            Traits traits,
            DungeonIntent intent,
            DungeonPlan plan
    ) {
        if (state == null) return Direction.WAIT;
        List<Direction> candidates = legalDirections(state);
        if (candidates.isEmpty()) return Direction.WAIT;

        Direction best = candidates.get(0);
        double bestScore = -Double.MAX_VALUE;
        Random tieBreak = new Random(state.seed ^ ((long) state.turn << 32) ^ state.floor);
        for (Direction direction : candidates) {
            double score = scoreDirection(state, traits, direction, intent, plan)
                    + tieBreak.nextDouble() * 0.025;
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    static double scoreDirection(DungeonState state, Traits traits, Direction direction) {
        return scoreDirection(state, traits, direction,
                DungeonIntent.localFallback(state, traits, "人格ベース"), null);
    }

    static double scoreDirection(
            DungeonState state,
            Traits traits,
            Direction direction,
            DungeonIntent intent
    ) {
        return scoreDirection(state, traits, direction, intent, null);
    }

    static double scoreDirection(
            DungeonState state,
            Traits traits,
            Direction direction,
            DungeonIntent intent,
            DungeonPlan plan
    ) {
        if (state == null || traits == null || direction == null) return -Double.MAX_VALUE;
        int nx = state.playerX + direction.dx;
        int ny = state.playerY + direction.dy;
        if (direction != Direction.WAIT && !state.walkable(nx, ny)) return -Double.MAX_VALUE;

        double e = traits.extraversion / 100.0;
        double n = traits.neuroticism / 100.0;
        double a = traits.agreeableness / 100.0;
        double c = traits.conscientiousness / 100.0;
        double o = traits.openness / 100.0;

        double planRisk = plan == null ? 0.5 : plan.riskTolerance;
        double planCombat = plan == null ? 0.5 : plan.combatPreference;
        double planExplore = plan == null ? 0.5 : plan.explorationPreference;
        double planProgress = plan == null ? 0.5 : plan.progressPreference;
        double planPersistence = plan == null ? 0.5 : plan.persistence;
        String strategy = plan == null
                ? DungeonPlan.STRATEGY_BALANCED : DungeonPlan.normalizeStrategy(plan.strategy);

        boolean stairsKnown = DungeonPerception.stairsKnown(state);
        int currentStairDistance = stairsKnown
                ? knownPathDistance(state, state.playerX, state.playerY,
                state.stairsX(), state.stairsY()) : 999;
        int nextStairDistance = stairsKnown
                ? knownPathDistance(state, nx, ny, state.stairsX(), state.stairsY()) : 999;
        int stairGain = currentStairDistance < 999 && nextStairDistance < 999
                ? currentStairDistance - nextStairDistance : 0;

        int currentEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        int nextEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(state, nx, ny);
        DungeonState.Enemy targetEnemy = state.enemyAt(nx, ny);
        boolean attacksEnemy = targetEnemy != null
                && DungeonPerception.isVisible(state, targetEnemy.x, targetEnemy.y);
        boolean unvisited = state.inside(nx, ny) && !state.visited[ny][nx];

        DungeonIntent resolved = intent == null
                ? DungeonIntent.localFallback(state, traits, "intentなし") : intent;
        String effectiveMode = effectiveMode(state, resolved, plan);
        Direction progressDirection = progressDirection(state, plan);

        double score = 0.0;
        if (stairsKnown) {
            score += stairGain * (0.25 + 2.7 * c) * (0.35 + 1.20 * planProgress);
        }
        if (unvisited) {
            score += 0.35 + 2.5 * o + 1.5 * planExplore;
        }

        if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
            int enemyApproach = currentEnemyDistance - nextEnemyDistance;
            score += enemyApproach * (1.8 * e - 2.8 * n - 0.8 * a);
            score += enemyApproach * (2.2 * (planCombat - 0.5) + 1.8 * (planRisk - 0.5));
            if (DungeonPlan.STRATEGY_HUNT.equals(strategy)) score += enemyApproach * 2.4;
            if (DungeonPlan.STRATEGY_SURVIVE.equals(strategy)) score -= enemyApproach * 2.8;
        }
        if (attacksEnemy) {
            score += 4.2 * e;
            score -= 4.6 * n;
            score -= 3.1 * a;
            score += 5.0 * (planCombat - 0.5) + 2.6 * (planRisk - 0.5);
            if (DungeonPlan.STRATEGY_HUNT.equals(strategy)) score += 4.8;
            if (DungeonPlan.STRATEGY_SURVIVE.equals(strategy)) score -= 5.2;
            if (targetEnemy.hp <= 2) score += 14.0 + 0.7 * c;
            if (currentEnemyDistance == 1 && !DungeonIntent.EVADE.equals(effectiveMode)) {
                score += 5.0;
            }
        } else if (nextEnemyDistance == 1) {
            score += 0.8 * e;
            score -= 2.0 * n;
            score -= 0.7 * a;
            score += 1.8 * (planRisk - 0.5);
        }

        if (direction == Direction.WAIT) {
            score -= 2.0 + 1.2 * c + 0.6 * o;
            score -= 1.4 * planPersistence;
        }
        if (stairsKnown && state.tileAt(nx, ny) == DungeonState.STAIRS) {
            score += 2.5 + 8.0 * planProgress + 1.5 * c + 1.0 * planPersistence;
            if (DungeonPlan.STRATEGY_EXPLORE.equals(strategy)
                    && firstStepToNearestFrontier(state) != null) {
                score -= 8.0 * planExplore;
            }
        }

        if (progressDirection != null && direction == progressDirection) {
            score += 2.5 + 7.0 * planProgress + 1.0 * c + 0.5 * o;
            score += 2.4 * (planPersistence - 0.5);
        } else if (progressDirection != null && direction != Direction.WAIT) {
            score -= 0.2 + 0.6 * planProgress;
        }

        if (resolved.preferredDirection == direction && direction != Direction.WAIT) {
            score += 1.2 * Math.max(0.30, resolved.confidence);
        }
        switch (effectiveMode) {
            case DungeonIntent.SEEK_STAIRS:
                if (stairsKnown) {
                    score += stairGain * (1.5 + 4.0 * planProgress);
                    if (state.tileAt(nx, ny) == DungeonState.STAIRS) {
                        score += 4.0 + 8.0 * planProgress;
                    }
                }
                if (progressDirection != null && direction == progressDirection) {
                    score += 1.0 + 4.0 * planProgress;
                }
                break;
            case DungeonIntent.ENGAGE:
                if (attacksEnemy) score += 3.0 + 6.0 * planCombat;
                if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
                    score += (currentEnemyDistance - nextEnemyDistance)
                            * (1.8 + 3.0 * planCombat);
                }
                break;
            case DungeonIntent.EVADE:
                if (attacksEnemy) score -= 8.0 + 4.0 * (1.0 - planRisk);
                if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
                    score += (nextEnemyDistance - currentEnemyDistance)
                            * (3.2 + 4.2 * (1.0 - planRisk));
                }
                if (!hasDistanceIncreasingMove(state, currentEnemyDistance) && attacksEnemy) {
                    score += 13.0;
                }
                break;
            case DungeonIntent.HOLD:
                if (direction == Direction.WAIT) score += 2.0;
                break;
            default:
                if (unvisited) score += 2.0 + 2.0 * planExplore + 0.8 * o;
                if (frontierGain(state, nx, ny) > frontierGain(state, state.playerX, state.playerY)) {
                    score += 1.0 + 1.6 * planExplore;
                }
                if (progressDirection != null && direction == progressDirection) {
                    score += 1.0 + 2.5 * planProgress;
                }
                break;
        }
        return score;
    }

    static String effectiveMode(DungeonState state, DungeonIntent intent) {
        return effectiveMode(state, intent, null);
    }

    static String effectiveMode(
            DungeonState state,
            DungeonIntent intent,
            DungeonPlan plan
    ) {
        if (state == null) return DungeonIntent.HOLD;
        boolean stairsKnown = DungeonPerception.stairsKnown(state);
        String legacyProgressMode = stairsKnown ? DungeonIntent.SEEK_STAIRS : DungeonIntent.EXPLORE;
        int visibleEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        double hpRate = state.maxHp <= 0 ? 0.0 : state.hp / (double) state.maxHp;
        int intentAge = intent == null ? Integer.MAX_VALUE : Math.max(0, state.turn - intent.turn);

        if (hpRate <= 0.25 && visibleEnemyDistance <= 3) return DungeonIntent.EVADE;

        if (intent != null && DungeonIntent.EVADE.equals(intent.mode)) {
            boolean critical = hpRate <= 0.30 && visibleEnemyDistance <= 3;
            boolean shortTacticalEvade = visibleEnemyDistance <= 2
                    && intentAge <= NORMAL_EVADE_LEASE_TURNS;
            if (critical || shortTacticalEvade) return DungeonIntent.EVADE;
        }
        if (intent != null && DungeonIntent.HOLD.equals(intent.mode)) {
            boolean oneTurnHold = visibleEnemyDistance < 999 && intentAge <= 1;
            if (oneTurnHold) return DungeonIntent.HOLD;
        }

        String strategy = plan == null
                ? DungeonPlan.STRATEGY_BALANCED : DungeonPlan.normalizeStrategy(plan.strategy);
        switch (strategy) {
            case DungeonPlan.STRATEGY_HUNT:
                return visibleEnemyDistance < 999 ? DungeonIntent.ENGAGE : DungeonIntent.EXPLORE;
            case DungeonPlan.STRATEGY_EXPLORE:
                return firstStepToNearestFrontier(state) != null
                        ? DungeonIntent.EXPLORE : legacyProgressMode;
            case DungeonPlan.STRATEGY_SURVIVE:
                if (visibleEnemyDistance <= 3) return DungeonIntent.EVADE;
                if (stairsKnown && plan != null && plan.progressPreference >= 0.65) {
                    return DungeonIntent.SEEK_STAIRS;
                }
                return DungeonIntent.EXPLORE;
            case DungeonPlan.STRATEGY_ADVANCE:
                return legacyProgressMode;
            default:
                break;
        }

        if (intent == null) return legacyProgressMode;
        if (DungeonIntent.ENGAGE.equals(intent.mode) && visibleEnemyDistance >= 999) {
            return legacyProgressMode;
        }
        if (DungeonIntent.SEEK_STAIRS.equals(intent.mode) && !stairsKnown) {
            return DungeonIntent.EXPLORE;
        }
        if (DungeonIntent.EVADE.equals(intent.mode) || DungeonIntent.HOLD.equals(intent.mode)) {
            return legacyProgressMode;
        }
        return intent.mode;
    }

    static Direction progressDirection(DungeonState state) {
        return progressDirection(state, null);
    }

    static Direction progressDirection(DungeonState state, DungeonPlan plan) {
        if (state == null) return null;
        String strategy = plan == null
                ? DungeonPlan.STRATEGY_BALANCED : DungeonPlan.normalizeStrategy(plan.strategy);
        boolean frontierFirst = DungeonPlan.STRATEGY_EXPLORE.equals(strategy)
                || DungeonPlan.STRATEGY_HUNT.equals(strategy);

        if (frontierFirst) {
            Direction frontier = firstStepToNearestFrontier(state);
            if (frontier != null) return frontier;
        }
        if (DungeonPerception.stairsKnown(state)) {
            Direction stairDirection = firstStepToKnownTarget(
                    state, state.stairsX(), state.stairsY());
            if (stairDirection != null) return stairDirection;
        }
        if (!frontierFirst) return firstStepToNearestFrontier(state);
        return null;
    }

    private static Direction firstStepToKnownTarget(DungeonState state, int targetX, int targetY) {
        if (!knownTraversable(state, targetX, targetY)) return null;
        boolean[][] seen = new boolean[state.height][state.width];
        Direction[][] first = new Direction[state.height][state.width];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{state.playerX, state.playerY});
        seen[state.playerY][state.playerX] = true;

        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            int x = point[0];
            int y = point[1];
            if (x == targetX && y == targetY) return first[y][x];
            for (Direction direction : orderedDirections()) {
                int nx = x + direction.dx;
                int ny = y + direction.dy;
                if (!knownTraversable(state, nx, ny) || seen[ny][nx]) continue;
                seen[ny][nx] = true;
                first[ny][nx] = x == state.playerX && y == state.playerY
                        ? direction : first[y][x];
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static Direction firstStepToNearestFrontier(DungeonState state) {
        boolean[][] seen = new boolean[state.height][state.width];
        Direction[][] first = new Direction[state.height][state.width];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{state.playerX, state.playerY});
        seen[state.playerY][state.playerX] = true;

        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            int x = point[0];
            int y = point[1];
            if (!(x == state.playerX && y == state.playerY) && isFrontierCell(state, x, y)) {
                return first[y][x];
            }
            for (Direction direction : orderedDirections()) {
                int nx = x + direction.dx;
                int ny = y + direction.dy;
                if (!knownTraversable(state, nx, ny) || seen[ny][nx]) continue;
                seen[ny][nx] = true;
                first[ny][nx] = x == state.playerX && y == state.playerY
                        ? direction : first[y][x];
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    static int knownPathDistance(
            DungeonState state,
            int startX,
            int startY,
            int targetX,
            int targetY
    ) {
        if (!knownTraversable(state, startX, startY)
                || !knownTraversable(state, targetX, targetY)) return 999;
        boolean[][] seen = new boolean[state.height][state.width];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY, 0});
        seen[startY][startX] = true;
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            if (point[0] == targetX && point[1] == targetY) return point[2];
            for (Direction direction : orderedDirections()) {
                int nx = point[0] + direction.dx;
                int ny = point[1] + direction.dy;
                if (!knownTraversable(state, nx, ny) || seen[ny][nx]) continue;
                seen[ny][nx] = true;
                queue.add(new int[]{nx, ny, point[2] + 1});
            }
        }
        return 999;
    }

    private static boolean knownTraversable(DungeonState state, int x, int y) {
        return state != null
                && state.inside(x, y)
                && state.visited[y][x]
                && state.walkable(x, y);
    }

    private static boolean isFrontierCell(DungeonState state, int x, int y) {
        if (!knownTraversable(state, x, y)) return false;
        for (Direction direction : orderedDirections()) {
            int nx = x + direction.dx;
            int ny = y + direction.dy;
            if (state.inside(nx, ny)
                    && !state.visited[ny][nx]
                    && !DungeonPerception.isVisible(state, nx, ny)) return true;
        }
        return false;
    }

    private static boolean hasDistanceIncreasingMove(DungeonState state, int currentDistance) {
        if (state == null || currentDistance >= 999) return false;
        for (Direction direction : orderedDirections()) {
            int nx = state.playerX + direction.dx;
            int ny = state.playerY + direction.dy;
            if (!state.walkable(nx, ny) || state.enemyAt(nx, ny) != null) continue;
            int nextDistance = DungeonPerception.nearestVisibleEnemyDistance(state, nx, ny);
            if (nextDistance > currentDistance) return true;
        }
        return false;
    }

    private static Direction[] orderedDirections() {
        return new Direction[]{Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT};
    }

    private static int frontierGain(DungeonState state, int x, int y) {
        if (!state.inside(x, y)) return 0;
        int count = 0;
        for (Direction direction : orderedDirections()) {
            int nx = x + direction.dx;
            int ny = y + direction.dy;
            if (state.inside(nx, ny)
                    && !state.visited[ny][nx]
                    && !DungeonPerception.isVisible(state, nx, ny)) count++;
        }
        return count;
    }

    private static List<Direction> legalDirections(DungeonState state) {
        List<Direction> result = new ArrayList<>();
        for (Direction direction : orderedDirections()) {
            int nx = state.playerX + direction.dx;
            int ny = state.playerY + direction.dy;
            if (state.walkable(nx, ny)) result.add(direction);
        }
        result.add(Direction.WAIT);
        return result;
    }
}
