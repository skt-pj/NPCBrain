package com.sktpj.npcbrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Compiles a Brain intent/plan into one legal dungeon action.
 *
 * v0.4.27 authority boundary: Traits are retained in the API for persistence/backward
 * compatibility, but this local compiler does not turn Big Five values or HP thresholds into a
 * second psychological decision. Personality belongs upstream in Brain valuation/action selection.
 */
final class DungeonPersonalityPolicy {
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
        return choose(state, traits, DungeonIntent.localFallback(state, traits, "中立fallback"), null);
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
                    + tieBreak.nextDouble() * 0.0001;
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    static double scoreDirection(DungeonState state, Traits traits, Direction direction) {
        return scoreDirection(
                state,
                traits,
                direction,
                DungeonIntent.localFallback(state, traits, "中立fallback"),
                null);
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
        if (state == null || direction == null) return -Double.MAX_VALUE;
        int nx = state.playerX + direction.dx;
        int ny = state.playerY + direction.dy;
        if (direction != Direction.WAIT && !state.walkable(nx, ny)) return -Double.MAX_VALUE;

        DungeonIntent resolved = intent == null
                ? DungeonIntent.localFallback(state, traits, "intentなし") : intent;
        String mode = effectiveMode(state, resolved, plan);
        Direction progress = progressDirection(state, plan);
        DungeonState.Enemy target = direction == Direction.WAIT ? null : state.enemyAt(nx, ny);
        boolean attacks = target != null && target.alive()
                && DungeonPerception.isVisible(state, target.x, target.y);
        int currentEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        int nextEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(state, nx, ny);
        boolean unvisited = direction != Direction.WAIT
                && state.inside(nx, ny) && !state.visited[ny][nx];

        double combat = plan == null ? 0.5 : plan.combatPreference;
        double risk = plan == null ? 0.5 : plan.riskTolerance;
        double explore = plan == null ? 0.5 : plan.explorationPreference;
        double progressWeight = plan == null ? 0.5 : plan.progressPreference;
        double persistence = plan == null ? 0.5 : plan.persistence;

        double score = 0.0;

        // A same-cycle Brain environment_action is an execution instruction, not a weak hint.
        if (resolved.isBrain()
                && resolved.preferredDirection == direction
                && direction != Direction.WAIT) {
            score += 40.0 + 40.0 * resolved.confidence;
        }
        if (resolved.isBrain()
                && DungeonIntent.HOLD.equals(mode)
                && direction == Direction.WAIT) {
            score += 80.0;
        }

        if (progress != null && direction == progress) {
            score += 3.0 + 8.0 * progressWeight;
        }
        if (unvisited) score += 1.0 + 6.0 * explore;

        if (DungeonPerception.stairsKnown(state)) {
            int current = knownPathDistance(
                    state, state.playerX, state.playerY, state.stairsX(), state.stairsY());
            int next = direction == Direction.WAIT ? current
                    : knownPathDistance(state, nx, ny, state.stairsX(), state.stairsY());
            if (current < 999 && next < 999) score += (current - next) * (2.0 + 6.0 * progressWeight);
            if (direction != Direction.WAIT && state.tileAt(nx, ny) == DungeonState.STAIRS) {
                score += 8.0 + 12.0 * progressWeight;
            }
        }

        switch (mode) {
            case DungeonIntent.ENGAGE:
                if (attacks) score += 20.0 + 20.0 * combat + 8.0 * risk;
                if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
                    score += (currentEnemyDistance - nextEnemyDistance) * (6.0 + 8.0 * combat);
                }
                if (direction == Direction.WAIT) score -= 8.0;
                break;
            case DungeonIntent.EVADE:
                if (attacks) score -= 30.0;
                if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
                    score += (nextEnemyDistance - currentEnemyDistance) * (10.0 + 8.0 * (1.0 - risk));
                }
                if (direction == Direction.WAIT && currentEnemyDistance < 999) score -= 6.0;
                break;
            case DungeonIntent.HOLD:
                if (direction == Direction.WAIT) score += 30.0;
                else score -= 10.0;
                break;
            case DungeonIntent.SEEK_STAIRS:
                if (progress != null && direction == progress) score += 15.0;
                if (attacks) score += (combat - 0.5) * 8.0;
                break;
            default:
                if (unvisited) score += 8.0 + 8.0 * explore;
                if (frontierGain(state, nx, ny) > frontierGain(state, state.playerX, state.playerY)) {
                    score += 3.0 + 4.0 * explore;
                }
                if (attacks) score += (combat - 0.5) * 8.0;
                break;
        }

        if (direction == Direction.WAIT && !DungeonIntent.HOLD.equals(mode)) {
            score -= 2.0 + 4.0 * persistence;
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
        int enemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);

        if (intent != null && intent.isBrain()) {
            if (DungeonIntent.SEEK_STAIRS.equals(intent.mode) && !stairsKnown) {
                return DungeonIntent.EXPLORE;
            }
            if (DungeonIntent.ENGAGE.equals(intent.mode) && enemyDistance >= 999) {
                return DungeonIntent.EXPLORE;
            }
            // EVADE/HOLD deliberately do not expire by HP, distance or turn age.
            return intent.mode;
        }

        String strategy = plan == null
                ? DungeonPlan.STRATEGY_BALANCED : DungeonPlan.normalizeStrategy(plan.strategy);
        switch (strategy) {
            case DungeonPlan.STRATEGY_HUNT:
                return enemyDistance < 999 ? DungeonIntent.ENGAGE : DungeonIntent.EXPLORE;
            case DungeonPlan.STRATEGY_SURVIVE:
                return enemyDistance < 999 ? DungeonIntent.EVADE
                        : (stairsKnown ? DungeonIntent.SEEK_STAIRS : DungeonIntent.EXPLORE);
            case DungeonPlan.STRATEGY_ADVANCE:
                return stairsKnown ? DungeonIntent.SEEK_STAIRS : DungeonIntent.EXPLORE;
            case DungeonPlan.STRATEGY_EXPLORE:
                return DungeonIntent.EXPLORE;
            default:
                if (intent != null) {
                    if (DungeonIntent.SEEK_STAIRS.equals(intent.mode) && !stairsKnown) {
                        return DungeonIntent.EXPLORE;
                    }
                    return intent.mode;
                }
                return stairsKnown ? DungeonIntent.SEEK_STAIRS : DungeonIntent.EXPLORE;
        }
    }

    static Direction progressDirection(DungeonState state) {
        return progressDirection(state, null);
    }

    static Direction progressDirection(DungeonState state, DungeonPlan plan) {
        if (state == null) return null;
        String strategy = plan == null
                ? DungeonPlan.STRATEGY_BALANCED : DungeonPlan.normalizeStrategy(plan.strategy);
        boolean exploreFirst = DungeonPlan.STRATEGY_EXPLORE.equals(strategy)
                || DungeonPlan.STRATEGY_HUNT.equals(strategy);
        if (exploreFirst) {
            Direction frontier = firstStepToNearestFrontier(state);
            if (frontier != null) return frontier;
        }
        if (DungeonPerception.stairsKnown(state)) {
            Direction stairs = firstStepToKnownTarget(state, state.stairsX(), state.stairsY());
            if (stairs != null) return stairs;
        }
        return firstStepToNearestFrontier(state);
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

    private static Direction[] orderedDirections() {
        return new Direction[]{Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT};
    }

    private static Direction firstStepToKnownTarget(
            DungeonState state,
            int targetX,
            int targetY
    ) {
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
            if (!(x == state.playerX && y == state.playerY) && isFrontier(state, x, y)) {
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
        // If the player itself is the only frontier, move toward any legal unexplored/unknown edge.
        for (Direction direction : orderedDirections()) {
            int nx = state.playerX + direction.dx;
            int ny = state.playerY + direction.dy;
            if (state.walkable(nx, ny)) return direction;
        }
        return null;
    }

    private static boolean isFrontier(DungeonState state, int x, int y) {
        if (!knownTraversable(state, x, y)) return false;
        for (Direction direction : orderedDirections()) {
            int nx = x + direction.dx;
            int ny = y + direction.dy;
            if (!state.inside(nx, ny) || state.visited[ny][nx]) continue;
            // A currently visible wall is already grounded as a wall; an out-of-view cell remains
            // unknown regardless of its backing generated tile, so hidden tile contents cannot leak.
            if (DungeonPerception.isVisible(state, nx, ny)
                    && state.tileAt(nx, ny) == DungeonState.WALL) continue;
            return true;
        }
        return false;
    }

    private static boolean knownTraversable(DungeonState state, int x, int y) {
        if (state == null || !state.inside(x, y)) return false;
        if (x == state.playerX && y == state.playerY) return true;
        return state.visited[y][x] && state.walkable(x, y);
    }

    private static int knownPathDistance(
            DungeonState state,
            int startX,
            int startY,
            int targetX,
            int targetY
    ) {
        if (state == null || !state.inside(startX, startY)
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

    private static int frontierGain(DungeonState state, int x, int y) {
        if (state == null || !state.inside(x, y)) return 0;
        int gain = 0;
        for (Direction direction : orderedDirections()) {
            int nx = x + direction.dx;
            int ny = y + direction.dy;
            if (!state.inside(nx, ny) || state.visited[ny][nx]) continue;
            if (DungeonPerception.isVisible(state, nx, ny)
                    && state.tileAt(nx, ny) == DungeonState.WALL) continue;
            gain++;
        }
        return gain;
    }
}
