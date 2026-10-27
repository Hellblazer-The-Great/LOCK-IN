package com.lockin.game;

public final class CollisionManager {
    private CollisionManager() {}
    public static void resolveWorld(Player p) {
        p.x = Math.max(0, Math.min(GameWorld.WIDTH - Player.WIDTH, p.x));
        if (p.y + Player.HEIGHT >= GameWorld.GROUND_Y) {
            p.y = GameWorld.GROUND_Y - Player.HEIGHT;
            p.velocityY = 0;
            p.grounded = true;
        } else p.grounded = false;
    }
    public static boolean overlaps(float l, float t, float r, float b,
                                   float l2, float t2, float r2, float b2) {
        return l < r2 && r > l2 && t < b2 && b > t2;
    }
}
