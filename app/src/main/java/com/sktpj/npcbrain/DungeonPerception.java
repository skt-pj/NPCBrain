package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DungeonPerception {
    static final int VISIBLE_RADIUS = 5;

    private DungeonPerception() {
    }

    static void refreshExploration(DungeonState state) {
        if (state == null) return;
        for (int y = Math.max(0, state.playerY - VISIBLE_RADIUS);
             y <= Math.min(state.height - 1, state.playerY + VISIBLE_RADIUS); y++) {
            for (int x = Math.max(0, state.playerX - VISIBLE_RADIUS);
                 x <= Math.min(state.width - 1, state.playerX + VISIBLE_RADIUS); x++) {
                if (isVisible(state, x, y)) state.markVisited(x, y);
            }
        }
    }

    static boolean isVisible(DungeonState state, int x, int y) {
        if (state == null || !state.inside(x, y)) return false;
        int dx = x - state.playerX;
        int dy = y - state.playerY;
        if (Math.abs(dx) + Math.abs(dy) > VISIBLE_RADIUS) return false;
        return hasLineOfSight(state, state.playerX, state.playerY, x, y);
    }

    static boolean stairsKnown(DungeonState state) {
        if (state == null) return false;
        int x = state.stairsX();
        int y = state.stairsY();
        return x >= 0 && y >= 0 && state.inside(x, y) && state.visited[y][x];
    }

    static int knownStairDistance(DungeonState state, int x, int y) {
        if (!stairsKnown(state)) return 999;
        return manhattan(x, y, state.stairsX(), state.stairsY());
    }

    static int nearestVisibleEnemyDistance(DungeonState state, int x, int y) {
        int best = 999;
        if (state == null) return best;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !isVisible(state, enemy.x, enemy.y)) continue;
            best = Math.min(best, manhattan(x, y, enemy.x, enemy.y));
        }
        return best;
    }

    static List<String> visibleEnemyIds(DungeonState state) {
        List<String> result = new ArrayList<>();
        if (state == null) return result;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (enemy.alive() && isVisible(state, enemy.x, enemy.y)) result.add(enemy.id);
        }
        result.sort(String::compareTo);
        return result;
    }

    static boolean adjacentVisibleEnemy(DungeonState state) {
        if (state == null) return false;
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !isVisible(state, enemy.x, enemy.y)) continue;
            if (DungeonEngine.canAttack(state.playerX, state.playerY, enemy.x, enemy.y)) return true;
        }
        return false;
    }

    static JSONObject buildRuntimeJson(DungeonState state, String triggerReason) {
        return buildRuntimeJson(
                state,
                triggerReason,
                DungeonObjective.none(),
                null);
    }

    static JSONObject buildRuntimeJson(
            DungeonState state,
            String triggerReason,
            DungeonObjective objective,
            DungeonPlan existingPlan
    ) {
        JSONObject root = new JSONObject();
        try {
            root.put("mode", "dungeon_turn");
            root.put("planning_mode", "strategic_replan");
            root.put("trigger_reason", triggerReason == null ? "objective_changed" : triggerReason);
            if (state == null) return root;

            refreshExploration(state);
            DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
            root.put("floor", state.floor);
            root.put("turn", state.turn);
            root.put("hp", state.hp);
            root.put("max_hp", state.maxHp);
            root.put("hp_ratio", state.maxHp <= 0 ? 0.0 : state.hp / (double) state.maxHp);
            root.put("player", point(state.playerX, state.playerY));
            root.put("last_action", state.lastAction);
            root.put("alive_enemy_count", state.aliveEnemyCount());
            root.put("visibility_radius", VISIBLE_RADIUS);

            JSONObject objectiveJson = new JSONObject();
            objectiveJson.put("type", goal.type);
            objectiveJson.put("target_floor", goal.targetFloor);
            objectiveJson.put("current_floor", state.floor);
            objectiveJson.put("completed", goal.isComplete(state.floor));
            objectiveJson.put("instruction", goal.isActive()
                    ? "Reach the top floor while staying alive. Decide a durable strategy, not a move-by-move script."
                    : "No long-term dungeon objective is active.");
            root.put("objective", objectiveJson);
            if (existingPlan != null && existingPlan.matches(goal)) {
                root.put("existing_plan", existingPlan.toJson());
            }

            JSONObject stairs = new JSONObject();
            boolean known = stairsKnown(state);
            stairs.put("known", known);
            if (known) {
                stairs.put("x", state.stairsX());
                stairs.put("y", state.stairsY());
                stairs.put("distance", knownStairDistance(state, state.playerX, state.playerY));
                stairs.put("known_path_distance", DungeonProgressMonitor.knownStairPathDistance(state));
            }
            root.put("stairs", stairs);
            root.put("visible_enemies", visibleEnemiesJson(state));
            root.put("visible_cells", visibleCellsJson(state));
            root.put("candidate_actions", candidateActions(state));
            root.put("grounded_progress", groundedProgressJson(state));
            root.put("explored_cell_count", exploredCellCount(state));
            root.put("runtime_contract",
                    "Use only the explicit objective, visible_enemies, visible_cells, explored state, known stairs, grounded_progress, existing_plan and candidate_actions. Never infer hidden map, hidden enemies or undiscovered stairs. Think at strategy level for the persistent objective; routine movement, exploration, combat and floor transitions will be executed locally without another API call. Evade and hold are short tactical responses, not indefinite goals. Choose one feasible environment_action that reflects the strategy. Personality may change attention/value/action preference but may not change observed facts or game rules.");
        } catch (Exception ignored) {
        }
        return root;
    }

    private static JSONObject groundedProgressJson(DungeonState state) {
        JSONObject object = new JSONObject();
        try {
            DungeonPersonalityPolicy.Direction direction =
                    DungeonPersonalityPolicy.progressDirection(state);
            object.put("goal", stairsKnown(state) ? "known_stairs" : "explore_frontier");
            object.put("direction", DungeonIntent.directionName(direction));
            object.put("available", direction != null
                    && direction != DungeonPersonalityPolicy.Direction.WAIT);
            object.put("knowledge_scope", "visited_walkable_cells_only");
        } catch (Exception ignored) {
        }
        return object;
    }

    private static JSONArray visibleEnemiesJson(DungeonState state) {
        JSONArray array = new JSONArray();
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !isVisible(state, enemy.x, enemy.y)) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("id", enemy.id);
                item.put("x", enemy.x);
                item.put("y", enemy.y);
                item.put("hp", enemy.hp);
                item.put("distance", manhattan(
                        state.playerX, state.playerY, enemy.x, enemy.y));
                item.put("adjacent", DungeonEngine.canAttack(
                        state.playerX, state.playerY, enemy.x, enemy.y));
            } catch (Exception ignored) {
            }
            array.put(item);
        }
        return array;
    }

    private static JSONArray visibleCellsJson(DungeonState state) {
        JSONArray array = new JSONArray();
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                if (!isVisible(state, x, y)) continue;
                JSONObject item = new JSONObject();
                try {
                    item.put("x", x);
                    item.put("y", y);
                    item.put("tile", tileName(state.tileAt(x, y)));
                    item.put("explored", state.visited[y][x]);
                } catch (Exception ignored) {
                }
                array.put(item);
            }
        }
        return array;
    }

    private static JSONArray candidateActions(DungeonState state) {
        JSONArray result = new JSONArray();
        DungeonPersonalityPolicy.Direction[] directions = {
                DungeonPersonalityPolicy.Direction.UP,
                DungeonPersonalityPolicy.Direction.RIGHT,
                DungeonPersonalityPolicy.Direction.DOWN,
                DungeonPersonalityPolicy.Direction.LEFT,
                DungeonPersonalityPolicy.Direction.WAIT
        };
        for (DungeonPersonalityPolicy.Direction direction : directions) {
            int nx = state.playerX + direction.dx;
            int ny = state.playerY + direction.dy;
            boolean wait = direction == DungeonPersonalityPolicy.Direction.WAIT;
            boolean walkable = wait || state.walkable(nx, ny);
            DungeonState.Enemy enemy = wait ? null : state.enemyAt(nx, ny);
            boolean attacks = enemy != null && isVisible(state, enemy.x, enemy.y);
            JSONObject item = new JSONObject();
            try {
                item.put("direction", DungeonIntent.directionName(direction));
                item.put("type", attacks ? "attack" : (wait ? "wait" : "move"));
                item.put("walkable", walkable);
                item.put("attacks_enemy", attacks);
                item.put("target_id", attacks ? enemy.id : "");
                item.put("unexplored", state.inside(nx, ny) && !state.visited[ny][nx]);
                int stairDistance = knownStairDistance(state, nx, ny);
                if (stairDistance < 999) item.put("known_stair_distance", stairDistance);
                int enemyDistance = nearestVisibleEnemyDistance(state, nx, ny);
                if (enemyDistance < 999) item.put("visible_enemy_distance", enemyDistance);
            } catch (Exception ignored) {
            }
            result.put(item);
        }
        return result;
    }

    private static int exploredCellCount(DungeonState state) {
        int count = 0;
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) if (state.visited[y][x]) count++;
        }
        return count;
    }

    private static JSONObject point(int x, int y) {
        JSONObject point = new JSONObject();
        try {
            point.put("x", x);
            point.put("y", y);
        } catch (Exception ignored) {
        }
        return point;
    }

    private static String tileName(int tile) {
        if (tile == DungeonState.WALL) return "wall";
        if (tile == DungeonState.STAIRS) return "stairs";
        return "floor";
    }

    private static boolean hasLineOfSight(
            DungeonState state,
            int x0,
            int y0,
            int x1,
            int y1
    ) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (!(x == x1 && y == y1)) {
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
            if (x == x1 && y == y1) return true;
            if (state.tileAt(x, y) == DungeonState.WALL) return false;
        }
        return true;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}
