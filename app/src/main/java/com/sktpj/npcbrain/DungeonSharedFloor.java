package com.sktpj.npcbrain;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Canonical shared world state for one dungeon floor. Actor HP/position/fog are not authoritative here. */
final class DungeonSharedFloor {
    final int floor;
    final int entryX;
    final int entryY;
    final long revision;
    private final DungeonState world;

    private DungeonSharedFloor(int floor, int entryX, int entryY, long revision, DungeonState world) {
        this.floor = Math.max(1, floor);
        this.entryX = entryX;
        this.entryY = entryY;
        this.revision = Math.max(1L, revision);
        this.world = deepCopy(world);
    }

    static DungeonSharedFloor fromState(DungeonState state, int entryX, int entryY, long revision) {
        if (state == null) return null;
        DungeonState copy = deepCopy(state);
        if (copy == null) return null;
        clearVisited(copy.visited);
        copy.playerX = clampX(copy, entryX);
        copy.playerY = clampY(copy, entryY);
        if (!copy.walkable(copy.playerX, copy.playerY)) {
            int[] fallback = firstWalkable(copy);
            copy.playerX = fallback[0];
            copy.playerY = fallback[1];
        }
        clearVisited(copy.visited);
        copy.markVisited(copy.playerX, copy.playerY);
        copy.turn = 0;
        copy.hp = copy.maxHp;
        copy.lastAction = "";
        return new DungeonSharedFloor(
                state.floor, copy.playerX, copy.playerY, revision, copy);
    }

    DungeonSharedFloor withWorld(DungeonState state) {
        return fromState(state, entryX, entryY, revision + 1L);
    }

    DungeonState attach(DungeonState actor, boolean freshFog) {
        if (actor == null || actor.floor != floor) return null;
        int[][] tiles = copyTiles(world.tiles);
        boolean[][] visited = freshFog
                ? new boolean[world.height][world.width]
                : copyVisited(actor.visited, world.width, world.height);
        List<DungeonState.Enemy> enemies = copyEnemies(world.enemies);
        DungeonState combined = new DungeonState(
                floor,
                actor.turn,
                world.width,
                world.height,
                tiles,
                visited,
                actor.playerX,
                actor.playerY,
                actor.hp,
                actor.maxHp,
                world.seed,
                actor.lastAction,
                enemies);
        if (freshFog) {
            clearVisited(combined.visited);
            combined.markVisited(combined.playerX, combined.playerY);
        }
        return combined;
    }

    boolean sameWorld(DungeonState state) {
        if (state == null || state.floor != floor
                || state.width != world.width || state.height != world.height
                || state.seed != world.seed || state.enemies.size() != world.enemies.size()) return false;
        for (int y = 0; y < world.height; y++) {
            for (int x = 0; x < world.width; x++) {
                if (state.tiles[y][x] != world.tiles[y][x]) return false;
            }
        }
        for (int i = 0; i < world.enemies.size(); i++) {
            DungeonState.Enemy a = state.enemies.get(i);
            DungeonState.Enemy b = world.enemies.get(i);
            if (!a.id.equals(b.id) || a.x != b.x || a.y != b.y || a.hp != b.hp) return false;
        }
        return true;
    }

    void overwriteSharedPart(DungeonState target) {
        if (target == null || target.floor != floor
                || target.width != world.width || target.height != world.height) return;
        for (int y = 0; y < world.height; y++) {
            System.arraycopy(world.tiles[y], 0, target.tiles[y], 0, world.width);
        }
        target.seed = world.seed;
        target.enemies.clear();
        target.enemies.addAll(copyEnemies(world.enemies));
    }

    boolean enemyOccupies(int x, int y) {
        for (DungeonState.Enemy enemy : world.enemies) {
            if (enemy.alive() && enemy.x == x && enemy.y == y) return true;
        }
        return false;
    }

    boolean walkable(int x, int y) {
        return world.walkable(x, y);
    }

    int width() {
        return world.width;
    }

    int height() {
        return world.height;
    }

    JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("schema", 1);
            root.put("floor", floor);
            root.put("entry_x", entryX);
            root.put("entry_y", entryY);
            root.put("revision", revision);
            root.put("world", world.toJson());
        } catch (Exception ignored) {
        }
        return root;
    }

    static DungeonSharedFloor fromJson(JSONObject root) {
        if (root == null) return null;
        DungeonState world = DungeonState.fromJson(root.optJSONObject("world"));
        if (world == null) return null;
        int floor = Math.max(1, root.optInt("floor", world.floor));
        if (world.floor != floor) return null;
        return fromState(
                world,
                root.optInt("entry_x", world.playerX),
                root.optInt("entry_y", world.playerY),
                Math.max(1L, root.optLong("revision", 1L)));
    }

    private static DungeonState deepCopy(DungeonState state) {
        if (state == null) return null;
        try {
            return DungeonState.fromJson(new JSONObject(state.toJson().toString()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int[][] copyTiles(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int y = 0; y < source.length; y++) copy[y] = source[y].clone();
        return copy;
    }

    private static boolean[][] copyVisited(boolean[][] source, int width, int height) {
        boolean[][] copy = new boolean[height][width];
        if (source == null) return copy;
        for (int y = 0; y < Math.min(height, source.length); y++) {
            if (source[y] == null) continue;
            System.arraycopy(source[y], 0, copy[y], 0, Math.min(width, source[y].length));
        }
        return copy;
    }

    private static List<DungeonState.Enemy> copyEnemies(List<DungeonState.Enemy> source) {
        List<DungeonState.Enemy> copy = new ArrayList<>();
        if (source == null) return copy;
        for (DungeonState.Enemy enemy : source) {
            copy.add(new DungeonState.Enemy(enemy.id, enemy.x, enemy.y, enemy.hp));
        }
        return copy;
    }

    private static void clearVisited(boolean[][] visited) {
        if (visited == null) return;
        for (int y = 0; y < visited.length; y++) {
            if (visited[y] == null) continue;
            java.util.Arrays.fill(visited[y], false);
        }
    }

    private static int clampX(DungeonState state, int x) {
        return Math.max(0, Math.min(state.width - 1, x));
    }

    private static int clampY(DungeonState state, int y) {
        return Math.max(0, Math.min(state.height - 1, y));
    }

    private static int[] firstWalkable(DungeonState state) {
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                if (state.walkable(x, y) && state.tileAt(x, y) != DungeonState.STAIRS) {
                    return new int[]{x, y};
                }
            }
        }
        return new int[]{Math.max(0, state.playerX), Math.max(0, state.playerY)};
    }
}
