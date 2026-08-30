package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DungeonState {
    static final int WALL = 0;
    static final int FLOOR = 1;
    static final int STAIRS = 2;
    static final int CHEST = 3;

    static final class Enemy {
        final String id;
        int x;
        int y;
        int hp;

        Enemy(String id, int x, int y, int hp) {
            this.id = id == null ? "enemy" : id;
            this.x = x;
            this.y = y;
            this.hp = Math.max(0, hp);
        }

        boolean alive() {
            return hp > 0;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("x", x);
                object.put("y", y);
                object.put("hp", hp);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Enemy fromJson(JSONObject object) {
            if (object == null) return null;
            return new Enemy(
                    object.optString("id", "enemy"),
                    object.optInt("x", -1),
                    object.optInt("y", -1),
                    object.optInt("hp", 0));
        }
    }

    int floor;
    int turn;
    final int width;
    final int height;
    final int[][] tiles;
    final boolean[][] visited;
    int playerX;
    int playerY;
    int hp;
    final int maxHp;
    long seed;
    String lastAction;
    final List<Enemy> enemies;

    DungeonState(
            int floor,
            int turn,
            int width,
            int height,
            int[][] tiles,
            boolean[][] visited,
            int playerX,
            int playerY,
            int hp,
            int maxHp,
            long seed,
            String lastAction,
            List<Enemy> enemies
    ) {
        this.floor = Math.max(1, floor);
        this.turn = Math.max(0, turn);
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.visited = visited;
        this.playerX = playerX;
        this.playerY = playerY;
        this.maxHp = Math.max(1, maxHp);
        this.hp = Math.max(0, Math.min(this.maxHp, hp));
        this.seed = seed;
        this.lastAction = lastAction == null ? "" : lastAction;
        this.enemies = enemies == null ? new ArrayList<>() : enemies;
        markVisited(playerX, playerY);
    }

    boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    int tileAt(int x, int y) {
        return inside(x, y) ? tiles[y][x] : WALL;
    }

    boolean walkable(int x, int y) {
        int tile = tileAt(x, y);
        return tile == FLOOR || tile == STAIRS || tile == CHEST;
    }

    boolean hasChest() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tiles[y][x] == CHEST) return true;
            }
        }
        return false;
    }

    void markVisited(int x, int y) {
        if (inside(x, y)) visited[y][x] = true;
    }

    Enemy enemyAt(int x, int y) {
        for (Enemy enemy : enemies) {
            if (enemy.alive() && enemy.x == x && enemy.y == y) return enemy;
        }
        return null;
    }

    int stairsX() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tiles[y][x] == STAIRS) return x;
            }
        }
        return -1;
    }

    int stairsY() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tiles[y][x] == STAIRS) return y;
            }
        }
        return -1;
    }

    int aliveEnemyCount() {
        int count = 0;
        for (Enemy enemy : enemies) if (enemy.alive()) count++;
        return count;
    }

    JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("floor", floor);
            root.put("turn", turn);
            root.put("width", width);
            root.put("height", height);
            root.put("player_x", playerX);
            root.put("player_y", playerY);
            root.put("hp", hp);
            root.put("max_hp", maxHp);
            root.put("seed", seed);
            root.put("last_action", lastAction);

            JSONArray tileRows = new JSONArray();
            JSONArray visitedRows = new JSONArray();
            for (int y = 0; y < height; y++) {
                JSONArray tileRow = new JSONArray();
                JSONArray visitedRow = new JSONArray();
                for (int x = 0; x < width; x++) {
                    tileRow.put(tiles[y][x]);
                    visitedRow.put(visited[y][x]);
                }
                tileRows.put(tileRow);
                visitedRows.put(visitedRow);
            }
            root.put("tiles", tileRows);
            root.put("visited", visitedRows);

            JSONArray enemyArray = new JSONArray();
            for (Enemy enemy : enemies) enemyArray.put(enemy.toJson());
            root.put("enemies", enemyArray);
        } catch (Exception ignored) {
        }
        return root;
    }

    static DungeonState fromJson(JSONObject root) {
        if (root == null) return null;
        try {
            int width = root.getInt("width");
            int height = root.getInt("height");
            if (width < 3 || height < 3 || width > 64 || height > 64) return null;

            JSONArray tileRows = root.getJSONArray("tiles");
            JSONArray visitedRows = root.optJSONArray("visited");
            if (tileRows.length() != height) return null;
            int[][] tiles = new int[height][width];
            boolean[][] visited = new boolean[height][width];
            for (int y = 0; y < height; y++) {
                JSONArray tileRow = tileRows.getJSONArray(y);
                if (tileRow.length() != width) return null;
                JSONArray visitedRow = visitedRows != null && y < visitedRows.length()
                        ? visitedRows.optJSONArray(y) : null;
                for (int x = 0; x < width; x++) {
                    int tile = tileRow.getInt(x);
                    if (tile != WALL && tile != FLOOR && tile != STAIRS && tile != CHEST) return null;
                    tiles[y][x] = tile;
                    visited[y][x] = visitedRow != null && visitedRow.optBoolean(x, false);
                }
            }

            List<Enemy> enemies = new ArrayList<>();
            JSONArray enemyArray = root.optJSONArray("enemies");
            if (enemyArray != null) {
                for (int i = 0; i < enemyArray.length(); i++) {
                    Enemy enemy = Enemy.fromJson(enemyArray.optJSONObject(i));
                    if (enemy != null && enemy.x >= 0 && enemy.y >= 0
                            && enemy.x < width && enemy.y < height) {
                        enemies.add(enemy);
                    }
                }
            }

            int playerX = root.getInt("player_x");
            int playerY = root.getInt("player_y");
            if (playerX < 0 || playerY < 0 || playerX >= width || playerY >= height) return null;

            DungeonState state = new DungeonState(
                    root.optInt("floor", 1),
                    root.optInt("turn", 0),
                    width,
                    height,
                    tiles,
                    visited,
                    playerX,
                    playerY,
                    root.optInt("hp", 10),
                    root.optInt("max_hp", 10),
                    root.optLong("seed", 1L),
                    root.optString("last_action", ""),
                    enemies);
            return state.walkable(playerX, playerY) && state.stairsX() >= 0 ? state : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
