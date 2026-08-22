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
                ? DungeonPerception.knownStairDistance(state, state.playerX, state.playerY) : 999;
        int nextStairDistance = stairsKnown
                ? DungeonPerception.knownStairDistance(state, nx, ny) : 999;
        int stairGain = stairsKnown ? currentStairDistance - nextStairDistance : 0;

        int currentEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        int nextEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(state, nx, ny);
        DungeonState.Enemy targetEnemy = state.enemyAt(nx, ny);
        boolean attacksEnemy = targetEnemy != null
                && DungeonPerception.isVisible(state, targetEnemy.x, targetEnemy.y);
        boolean unvisited = state.inside(nx, ny) && !state.visited[ny][nx];

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
            if (targetEnemy.hp <= 2) score += 0.7 * c;
        } else if (nextEnemyDistance == 1) {
            score += 0.8 * e;
            score -= 2.0 * n;
            score -= 0.7 * a;
        }

        if (direction == Direction.WAIT) score -= 1.2 + 1.0 * c + 0.4 * o;
        if (stairsKnown && state.tileAt(nx, ny) == DungeonState.STAIRS) {
            score += 6.0 + 2.0 * c;
        }

        DungeonIntent resolved = intent == null
                ? DungeonIntent.localFallback(state, traits, "intentなし") : intent;
        if (resolved.preferredDirection == direction && direction != Direction.WAIT) {
            score += 3.2 * Math.max(0.35, resolved.confidence);
        }
        switch (resolved.mode) {
            case DungeonIntent.SEEK_STAIRS:
                if (stairsKnown) {
                    score += stairGain * 5.0;
                    if (state.tileAt(nx, ny) == DungeonState.STAIRS) score += 8.0;
                } else if (unvisited) {
                    score += 1.6 + 1.5 * o;
                }
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
                break;
            case DungeonIntent.HOLD:
                if (direction == Direction.WAIT) score += 4.0;
                break;
            default:
                if (unvisited) score += 3.0 + 1.0 * o;
                if (frontierGain(state, nx, ny) > frontierGain(state, state.playerX, state.playerY)) {
                    score += 1.2;
                }
                break;
        }
        return score;
    }

    private static int frontierGain(DungeonState state, int x, int y) {
        if (!state.inside(x, y)) return 0;
        int count = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (state.inside(nx, ny) && state.walkable(nx, ny) && !state.visited[ny][nx]) count++;
        }
        return count;
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
}
