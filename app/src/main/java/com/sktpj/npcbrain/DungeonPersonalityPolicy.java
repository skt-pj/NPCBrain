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
        return choose(state, traits, DungeonIntent.localFallback(state, traits, "人格ベース"));
    }

    static Direction choose(DungeonState state, Traits traits, DungeonIntent intent) {
        if (state == null) return Direction.WAIT;
        List<Direction> candidates = legalDirections(state);
        if (candidates.isEmpty()) return Direction.WAIT;

        Direction best = candidates.get(0);
        double bestScore = -Double.MAX_VALUE;
        Random tieBreak = new Random(state.seed ^ ((long) state.turn << 32) ^ state.floor);
        for (Direction direction : candidates) {
            double score = scoreDirection(state, traits, direction, intent)
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
                DungeonIntent.localFallback(state, traits, "人格ベース"));
    }

    static double scoreDirection(
            DungeonState state,
            Traits traits,
            Direction direction,
            DungeonIntent intent
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
        String effectiveMode = effectiveMode(state, resolved);
        Direction progressDirection = progressDirection(state);

        double score = 0.0;
        if (stairsKnown) score += stairGain * (0.7 + 2.7 * c);
        if (unvisited) score += 0.35 + 2.5 * o;

        if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
            int enemyApproach = currentEnemyDistance - nextEnemyDistance;
            score += enemyApproach * (1.8 * e - 2.8 * n - 0.8 * a);
        }
        if (attacksEnemy) {
            score += 4.2 * e;
            score -= 4.6 * n;
            score -= 3.1 * a;
            if (targetEnemy.hp <= 2) score += 14.0 + 0.7 * c;
            if (currentEnemyDistance == 1 && !DungeonIntent.EVADE.equals(effectiveMode)) {
                score += 5.0;
            }
        } else if (nextEnemyDistance == 1) {
            score += 0.8 * e;
            score -= 2.0 * n;
            score -= 0.7 * a;
        }

        if (direction == Direction.WAIT) score -= 2.0 + 1.2 * c + 0.6 * o;
        if (stairsKnown && state.tileAt(nx, ny) == DungeonState.STAIRS) {
            score += 8.0 + 2.5 * c;
        }

        if (progressDirection != null && direction == progressDirection) {
            score += 7.5 + 1.5 * c + 0.8 * o;
        } else if (progressDirection != null && direction != Direction.WAIT) {
            score -= 0.4;
        }

        if (resolved.preferredDirection == direction && direction != Direction.WAIT) {
            score += 2.2 * Math.max(0.30, resolved.confidence);
        }
        switch (effectiveMode) {
            case DungeonIntent.SEEK_STAIRS:
                if (stairsKnown) {
                    score += stairGain * 4.5;
                    if (state.tileAt(nx, ny) == DungeonState.STAIRS) score += 10.0;
                }
                if (progressDirection != null && direction == progressDirection) score += 3.5;
                break;
            case DungeonIntent.ENGAGE:
                if (attacksEnemy) score += 6.0;
                if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
                    score += (currentEnemyDistance - nextEnemyDistance) * 3.2;
                }
                break;
            case DungeonIntent.EVADE:
                if (attacksEnemy) score -= 8.0;
                if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
                    score += (nextEnemyDistance - currentEnemyDistance) * 5.0;
                }
                if (!hasDistanceIncreasingMove(state, currentEnemyDistance) && attacksEnemy) {
                    score += 13.0;
                }
                break;
            case DungeonIntent.HOLD:
                if (direction == Direction.WAIT) score += 2.0;
                break;
            default:
                if (unvisited) score += 3.0 + 1.0 * o;
                if (frontierGain(state, nx, ny) > frontierGain(state, state.playerX, state.playerY)) {
                    score += 1.2;
                }
                if (progressDirection != null && direction == progressDirection) score += 2.5;
                break;
        }
        return score;
    }

    static String effectiveMode(DungeonState state, DungeonIntent intent) {
        if (state == null) return DungeonIntent.HOLD;
        boolean stairsKnown = DungeonPerception.stairsKnown(state);
        String progressMode = stairsKnown ? DungeonIntent.SEEK_STAIRS : DungeonIntent.EXPLORE;
        if (intent == null) return progressMode;

        int visibleEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        double hpRate = state.maxHp <= 0 ? 0.0 : state.hp / (double) state.maxHp;
        int intentAge = Math.max(0, state.turn - intent.turn);

        if (DungeonIntent.EVADE.equals(intent.mode)) {
            boolean critical = hpRate <= 0.30 && visibleEnemyDistance <= 3;
            boolean shortTacticalEvade = visibleEnemyDistance <= 2
                    && intentAge <= NORMAL_EVADE_LEASE_TURNS;
            return critical || shortTacticalEvade ? DungeonIntent.EVADE : progressMode;
        }
        if (DungeonIntent.HOLD.equals(intent.mode)) {
            boolean oneTurnHold = visibleEnemyDistance < 999 && intentAge <= 1;
            return oneTurnHold ? DungeonIntent.HOLD : progressMode;
        }
        if (DungeonIntent.ENGAGE.equals(intent.mode) && visibleEnemyDistance >= 999) {
            return progressMode;
        }
        if (DungeonIntent.SEEK_STAIRS.equals(intent.mode) && !stairsKnown) {
            return DungeonIntent.EXPLORE;
        }
        return intent.mode;
    }

    static Direction progressDirection(DungeonState state) {
        if (state == null) return null;
        if (DungeonPerception.stairsKnown(state)) {
            Direction stairDirection = firstStepToKnownTarget(
                    state, state.stairsX(), state.stairsY());
            if (stairDirection != null) return stairDirection;
        }
        return firstStepToNearestFrontier(state);
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

    private static int knownPathDistance(
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
