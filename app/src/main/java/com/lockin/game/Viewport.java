package com.lockin.game;

/** Uniform letterbox scale shared by rendering and the existing touch-coordinate conversion. */
public final class Viewport {
    private Viewport() {}
    public static float scale(int width, int height) {
        if (width <= 0 || height <= 0) return 0;
        return Math.min(width / GameWorld.WIDTH, height / GameWorld.HEIGHT);
    }
}
