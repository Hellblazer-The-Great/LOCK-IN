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
    private static boolean verticalOverlap(Player p, Enemy e) {
        return p.y < e.y + Enemy.HEIGHT && p.y + Player.HEIGHT > e.y;
    }
    /** Initial overlap/descending into a body: move the player to the nearest valid side.
     * The enemy never pushes a non-overlapping player; corpses have no body collision. */
    public static void resolveBodies(Player p, Enemy e) {
        p.x = Math.max(0, Math.min(GameWorld.WIDTH - Player.WIDTH, p.x));
        e.x = Math.max(0, Math.min(GameWorld.WIDTH - Enemy.WIDTH, e.x));
        if (p.defeated() || e.defeated() || !verticalOverlap(p, e)
                || p.x + Player.WIDTH <= e.x || p.x >= e.x + Enemy.WIDTH) return;
        float left = e.x - Player.WIDTH, right = e.x + Enemy.WIDTH;
        boolean leftValid = left >= 0, rightValid = right <= GameWorld.WIDTH - Player.WIDTH;
        if (leftValid && (!rightValid || Math.abs(p.x - left) <= Math.abs(right - p.x))) p.x = left;
        else p.x = right;
    }
    private static boolean verticalSweep(float oldY, float newY, Enemy e, float enter, float exit) {
        float dy = newY - oldY;
        if (dy == 0) return oldY < e.y + Enemy.HEIGHT && oldY + Player.HEIGHT > e.y;
        float a = (e.y - oldY - Player.HEIGHT) / dy;
        float b = (e.y + Enemy.HEIGHT - oldY) / dy;
        return Math.max(enter, Math.min(a, b)) < Math.min(exit, Math.max(a, b));
    }
    /** Swept body intervals detect diagonal entry as well as horizontal crossing.
     * Crossing entirely above the enemy is allowed; descending through a corner is not. */
    public static void resolvePlayerMovement(Player p, Enemy e, float oldX, float oldY) {
        if (p.defeated() || e.defeated()) return;
        float dx = p.x - oldX;
        float t = -1;
        if (dx > 0 && oldX + Player.WIDTH <= e.x && p.x + Player.WIDTH > e.x)
            t = (e.x - oldX - Player.WIDTH) / dx;
        else if (dx < 0 && oldX >= e.x + Enemy.WIDTH && p.x < e.x + Enemy.WIDTH)
            t = (e.x + Enemy.WIDTH - oldX) / dx;
        if (t >= 0 && t <= 1) {
            float exit = dx > 0 ? (e.x + Enemy.WIDTH - oldX) / dx : (e.x - oldX - Player.WIDTH) / dx;
            if (verticalSweep(oldY, p.y, e, t, Math.min(1, exit)))
                p.x = dx > 0 ? e.x - Player.WIDTH : e.x + Enemy.WIDTH;
        }
        resolveBodies(p, e);
    }
    /** Clamp the pursuing enemy against the player's body, never displace the player. */
    public static void resolveEnemyMovement(Enemy e, Player p, float oldX) {
        if (p.defeated() || e.defeated() || !verticalOverlap(p, e)) return;
        if (oldX + Enemy.WIDTH <= p.x && e.x + Enemy.WIDTH > p.x) e.x = p.x - Enemy.WIDTH;
        else if (oldX >= p.x + Player.WIDTH && e.x < p.x + Player.WIDTH) e.x = p.x + Player.WIDTH;
        e.x = Math.max(0, Math.min(GameWorld.WIDTH - Enemy.WIDTH, e.x));
        resolveBodies(p, e);
    }
    public static boolean overlaps(float l, float t, float r, float b,
                                   float l2, float t2, float r2, float b2) {
        return l < r2 && r > l2 && t < b2 && b > t2;
    }
}
