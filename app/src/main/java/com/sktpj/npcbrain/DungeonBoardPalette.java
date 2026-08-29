package com.sktpj.npcbrain;

final class DungeonBoardPalette {
    // Pre-filter luminance roles: keep traversable floor quiet/light so moving sprites keep edge contrast.
    static final int UNEXPLORED_FILL = gray(18);
    static final int UNEXPLORED_MARK = gray(30);

    static final int VISIBLE_FLOOR_A = gray(188);
    static final int VISIBLE_FLOOR_B = gray(176);
    static final int VISIBLE_FLOOR_BORDER = gray(146);

    static final int HIDDEN_FLOOR_A = gray(92);
    static final int HIDDEN_FLOOR_B = gray(84);
    static final int HIDDEN_FLOOR_BORDER = gray(68);

    static final int VISIBLE_WALL_BODY = gray(116);
    static final int VISIBLE_WALL_TOP = gray(138);
    static final int VISIBLE_WALL_BOTTOM = gray(64);

    static final int HIDDEN_WALL_BODY = gray(68);
    static final int HIDDEN_WALL_TOP = gray(82);
    static final int HIDDEN_WALL_BOTTOM = gray(48);

    private DungeonBoardPalette() {
    }

    static int floorFill(boolean visible, int x, int y) {
        boolean alternate = ((x + y) & 1) != 0;
        if (visible) return alternate ? VISIBLE_FLOOR_B : VISIBLE_FLOOR_A;
        return alternate ? HIDDEN_FLOOR_B : HIDDEN_FLOOR_A;
    }

    static int floorBorder(boolean visible) {
        return visible ? VISIBLE_FLOOR_BORDER : HIDDEN_FLOOR_BORDER;
    }

    static int wallBody(boolean visible) {
        return visible ? VISIBLE_WALL_BODY : HIDDEN_WALL_BODY;
    }

    static int wallTop(boolean visible) {
        return visible ? VISIBLE_WALL_TOP : HIDDEN_WALL_TOP;
    }

    static int wallBottom(boolean visible) {
        return visible ? VISIBLE_WALL_BOTTOM : HIDDEN_WALL_BOTTOM;
    }

    static int luma(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int gray(int value) {
        int v = Math.max(0, Math.min(255, value));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }
}
