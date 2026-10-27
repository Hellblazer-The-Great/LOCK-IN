package com.lockin.game;

/** Dependency-free regression checks; run with tools/test-core.sh. */
public final class CoreChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void step(Game game, float seconds, int move) {
        int frames = Math.round(seconds * 120);
        for (int i = 0; i < frames; i++) game.update(1f / 120, move, false, false);
    }
    public static void main(String[] args) {
        Game g = new Game();
        step(g, 2, -1);
        check(g.player.x == 0, "left boundary");
        step(g, 5, 1);
        check(g.player.x == GameWorld.WIDTH - Player.WIDTH, "right boundary");
        check(g.player.facingRight, "right facing");
        g.update(1f / 60, 0, true, false);
        check(!g.player.grounded && g.player.y < GameWorld.GROUND_Y - Player.HEIGHT, "jump");
        float vy = g.player.velocityY;
        g.update(1f / 120, 0, true, false);
        check(g.player.velocityY > vy, "no air jump");
        step(g, 2, 0);
        check(g.player.grounded && g.player.velocityY == 0, "landing");
        Game a = new Game(), b = new Game();
        for (int i = 0; i < 60; i++) a.update(1f / 60, 1, false, false);
        for (int i = 0; i < 120; i++) b.update(1f / 120, 1, false, false);
        check(Math.abs(a.player.x - b.player.x) < .02f, "delta time movement");
        g = new Game();
        g.update(1f / 60, 0, false, true);
        check(g.enemy.health == 100, "out of range");
        step(g, 1, 0);
        g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.player.facingRight = false;
        g.update(1f / 60, 0, false, true);
        check(g.enemy.health == 100, "wrong facing");
        step(g, 1, 0);
        g.player.facingRight = true;
        g.update(1f / 60, 0, false, true);
        check(g.enemy.health == 75, "melee damage");
        for (int i = 0; i < 15; i++) g.update(1f / 120, 0, false, true);
        check(g.enemy.health == 75, "single hit and cooldown");
        for (int i = 0; i < 3; i++) {
            step(g, 1, 0);
            g.update(1f / 60, 0, false, true);
        }
        check(g.enemy.defeated(), "enemy defeat");
        g.enemy.takeDamage(100);
        check(g.enemy.health == 0, "health floor");
        g = new Game();
        g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.player.y = 0;
        g.player.grounded = false;
        g.update(1f / 60, 0, false, true);
        check(g.enemy.health == 100, "vertical separation");
        float x = g.player.x;
        g.update(Float.NaN, 1, false, false);
        check(g.player.x == x, "invalid dt ignored");
        System.out.println("Passed " + checks + " core checks");
    }
}
