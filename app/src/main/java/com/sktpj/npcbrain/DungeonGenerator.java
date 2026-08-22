package com.sktpj.npcbrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

final class DungeonGenerator {
    static final int WIDTH = 17;
    static final int HEIGHT = 17;
    private static final int MAX_GENERATION_ATTEMPTS = 8;

    private static final class Room {
        final int x;
        final int y;
        final int width;
        final int height;

        Room(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }

        boolean overlaps(Room other) {
            return x - 1 < other.x + other.width
                    && x + width + 1 > other.x
                    && y - 1 < other.y + other.height
                    && y + height + 1 > other.y;
        }
    }

    private DungeonGenerator() {
    }

    static DungeonState generate(long seed, int floor) {
        return generate(seed, floor, 10, 10, 0);
    }

    static DungeonState generate(long seed, int floor, int maxHp, int hp, int turn) {
        long attemptSeed = seed;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            DungeonState state = generateOnce(attemptSeed, floor, maxHp, hp, turn);
            if (state != null && isReachable(
                    state, state.playerX, state.playerY, state.stairsX(), state.stairsY())) {
                return state;
            }
            attemptSeed = mixSeed(attemptSeed, attempt + 1L);
        }
        return fallback(seed, floor, maxHp, hp, turn);
    }

    private static DungeonState generateOnce(long seed, int floor, int maxHp, int hp, int turn) {
        Random random = new Random(mixSeed(seed, floor));
        int[][] tiles = walls();
        boolean[][] visited = new boolean[HEIGHT][WIDTH];
        List<Room> rooms = new ArrayList<>();
        int desiredRooms = 3 + random.nextInt(4);

        for (int attempt = 0; attempt < 80 && rooms.size() < desiredRooms; attempt++) {
            int roomWidth = 3 + random.nextInt(4);
            int roomHeight = 3 + random.nextInt(4);
            int x = 1 + random.nextInt(Math.max(1, WIDTH - roomWidth - 2));
            int y = 1 + random.nextInt(Math.max(1, HEIGHT - roomHeight - 2));
            Room candidate = new Room(x, y, roomWidth, roomHeight);
            boolean collision = false;
            for (Room room : rooms) {
                if (candidate.overlaps(room)) {
                    collision = true;
                    break;
                }
            }
            if (collision) continue;
            carveRoom(tiles, candidate);
            if (!rooms.isEmpty()) connect(tiles, rooms.get(rooms.size() - 1), candidate, random);
            rooms.add(candidate);
        }
        if (rooms.size() < 2) return null;

        Room startRoom = rooms.get(0);
        Room stairRoom = rooms.get(rooms.size() - 1);
        int playerX = startRoom.centerX();
        int playerY = startRoom.centerY();
        int stairsX = stairRoom.centerX();
        int stairsY = stairRoom.centerY();
        if (playerX == stairsX && playerY == stairsY) return null;
        tiles[stairsY][stairsX] = DungeonState.STAIRS;

        List<DungeonState.Enemy> enemies = placeEnemies(
                tiles, playerX, playerY, stairsX, stairsY, floor, random);
        DungeonState state = new DungeonState(
                floor,
                turn,
                WIDTH,
                HEIGHT,
                tiles,
                visited,
                playerX,
                playerY,
                hp,
                maxHp,
                seed,
                floor + "F を探索開始",
                enemies);
        state.markVisited(playerX, playerY);
        return state;
    }

    private static List<DungeonState.Enemy> placeEnemies(
            int[][] tiles,
            int playerX,
            int playerY,
            int stairsX,
            int stairsY,
            int floor,
            Random random
    ) {
        List<DungeonState.Enemy> enemies = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        int enemyCount = Math.min(6, 2 + Math.max(0, floor - 1) / 2);
        for (int i = 0; i < enemyCount; i++) {
            boolean placed = false;
            for (int attempt = 0; attempt < 120; attempt++) {
                int x = 1 + random.nextInt(WIDTH - 2);
                int y = 1 + random.nextInt(HEIGHT - 2);
                if (tiles[y][x] != DungeonState.FLOOR) continue;
                if (Math.abs(x - playerX) + Math.abs(y - playerY) < 4) continue;
                if (x == stairsX && y == stairsY) continue;
                int key = y * WIDTH + x;
                if (!occupied.add(key)) continue;
                enemies.add(new DungeonState.Enemy(
                        "enemy_" + floor + "_" + i,
                        x,
                        y,
                        2 + Math.min(3, Math.max(0, floor - 1) / 3)));
                placed = true;
                break;
            }
            if (!placed) break;
        }
        return enemies;
    }

    private static DungeonState fallback(long seed, int floor, int maxHp, int hp, int turn) {
        int[][] tiles = walls();
        boolean[][] visited = new boolean[HEIGHT][WIDTH];
        for (int y = 2; y < HEIGHT - 2; y++) {
            for (int x = 2; x < WIDTH - 2; x++) tiles[y][x] = DungeonState.FLOOR;
        }
        int playerX = 3;
        int playerY = 3;
        int stairsX = WIDTH - 4;
        int stairsY = HEIGHT - 4;
        tiles[stairsY][stairsX] = DungeonState.STAIRS;
        Random random = new Random(mixSeed(seed, floor + 101L));
        List<DungeonState.Enemy> enemies = placeEnemies(
                tiles, playerX, playerY, stairsX, stairsY, floor, random);
        return new DungeonState(
                floor,
                turn,
                WIDTH,
                HEIGHT,
                tiles,
                visited,
                playerX,
                playerY,
                hp,
                maxHp,
                seed,
                floor + "F を探索開始",
                enemies);
    }

    private static int[][] walls() {
        int[][] tiles = new int[HEIGHT][WIDTH];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) tiles[y][x] = DungeonState.WALL;
        }
        return tiles;
    }

    private static void carveRoom(int[][] tiles, Room room) {
        for (int y = room.y; y < room.y + room.height; y++) {
            for (int x = room.x; x < room.x + room.width; x++) {
                tiles[y][x] = DungeonState.FLOOR;
            }
        }
    }

    private static void connect(int[][] tiles, Room a, Room b, Random random) {
        int ax = a.centerX();
        int ay = a.centerY();
        int bx = b.centerX();
        int by = b.centerY();
        if (random.nextBoolean()) {
            carveHorizontal(tiles, ax, bx, ay);
            carveVertical(tiles, ay, by, bx);
        } else {
            carveVertical(tiles, ay, by, ax);
            carveHorizontal(tiles, ax, bx, by);
        }
    }

    private static void carveHorizontal(int[][] tiles, int x1, int x2, int y) {
        int start = Math.min(x1, x2);
        int end = Math.max(x1, x2);
        for (int x = start; x <= end; x++) tiles[y][x] = DungeonState.FLOOR;
    }

    private static void carveVertical(int[][] tiles, int y1, int y2, int x) {
        int start = Math.min(y1, y2);
        int end = Math.max(y1, y2);
        for (int y = start; y <= end; y++) tiles[y][x] = DungeonState.FLOOR;
    }

    static boolean isReachable(DungeonState state, int startX, int startY, int targetX, int targetY) {
        if (state == null || !state.walkable(startX, startY) || !state.walkable(targetX, targetY)) {
            return false;
        }
        boolean[][] seen = new boolean[state.height][state.width];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        seen[startY][startX] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            if (point[0] == targetX && point[1] == targetY) return true;
            for (int[] dir : dirs) {
                int nx = point[0] + dir[0];
                int ny = point[1] + dir[1];
                if (!state.inside(nx, ny) || seen[ny][nx] || !state.walkable(nx, ny)) continue;
                seen[ny][nx] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return false;
    }

    static long nextFloorSeed(long currentSeed, int nextFloor) {
        return mixSeed(currentSeed + 0x9E3779B97F4A7C15L, nextFloor * 31L + 17L);
    }

    private static long mixSeed(long seed, long salt) {
        long value = seed ^ (salt * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }
}
