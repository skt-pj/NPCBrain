package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        if (state == null) return Direction.WAIT;
        List<Direction> candidates = legalDirections(state);
        if (candidates.isEmpty()) return Direction.WAIT;

        Direction best = candidates.get(0);
        double bestScore = -Double.MAX_VALUE;
        Random tieBreak = new Random(state.seed ^ ((long) state.turn << 32) ^ state.floor);
        for (Direction direction : candidates) {
            double score = scoreDirection(state, traits, direction) + tieBreak.nextDouble() * 0.025;
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    static double scoreDirection(DungeonState state, Traits traits, Direction direction) {
        if (state == null || traits == null || direction == null) return -Double.MAX_VALUE;
        int nx = state.playerX + direction.dx;
        int ny = state.playerY + direction.dy;
        if (direction != Direction.WAIT && !state.walkable(nx, ny)) return -Double.MAX_VALUE;

        double e = traits.extraversion / 100.0;
        double n = traits.neuroticism / 100.0;
        double a = traits.agreeableness / 100.0;
        double c = traits.conscientiousness / 100.0;
        double o = traits.openness / 100.0;

        int stairsX = state.stairsX();
        int stairsY = state.stairsY();
        int currentStairDistance = manhattan(state.playerX, state.playerY, stairsX, stairsY);
        int nextStairDistance = manhattan(nx, ny, stairsX, stairsY);
        int stairGain = currentStairDistance - nextStairDistance;

        int currentEnemyDistance = nearestEnemyDistance(state, state.playerX, state.playerY);
        int nextEnemyDistance = nearestEnemyDistance(state, nx, ny);
        DungeonState.Enemy targetEnemy = state.enemyAt(nx, ny);
        boolean attacksEnemy = targetEnemy != null;
        boolean unvisited = state.inside(nx, ny) && !state.visited[ny][nx];

        double score = 0.0;
        score += stairGain * (0.8 + 3.2 * c);
        if (unvisited) score += 0.3 + 2.4 * o;

        if (currentEnemyDistance < 999 && nextEnemyDistance < 999) {
            int enemyApproach = currentEnemyDistance - nextEnemyDistance;
            score += enemyApproach * (1.8 * e - 2.8 * n - 0.8 * a);
        }
        if (attacksEnemy) {
            score += 4.2 * e;
            score -= 4.6 * n;
            score -= 3.1 * a;
            if (targetEnemy.hp <= 2) score += 0.7 * c;
        } else if (nextEnemyDistance == 1) {
            score += 0.8 * e;
            score -= 2.0 * n;
            score -= 0.7 * a;
        }

        if (direction == Direction.WAIT) score -= 1.2 + 1.0 * c + 0.4 * o;
        if (state.tileAt(nx, ny) == DungeonState.STAIRS) score += 6.0 + 2.0 * c;
        return score;
    }

    private static List<Direction> legalDirections(DungeonState state) {
        List<Direction> result = new ArrayList<>();
        Direction[] ordered = {Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT};
        for (Direction direction : ordered) {
            int nx = state.playerX + direction.dx;
            int ny = state.playerY + direction.dy;
            if (state.walkable(nx, ny)) result.add(direction);
        }
        result.add(Direction.WAIT);
        return result;
    }

    private static int nearestEnemyDistance(DungeonState state, int x, int y) {
        int best = 999;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive()) continue;
            best = Math.min(best, manhattan(x, y, enemy.x, enemy.y));
        }
        return best;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        if (x2 < 0 || y2 < 0) return 999;
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}
