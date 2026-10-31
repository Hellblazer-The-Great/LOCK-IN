package com.lockin.game;

public final class CollisionChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static boolean clear(Player p, Enemy e) {
        return !CollisionManager.overlaps(p.x, p.y, p.x + Player.WIDTH, p.y + Player.HEIGHT,
                e.x, e.y, e.x + Enemy.WIDTH, e.y + Enemy.HEIGHT);
    }
    public static void main(String[] args) {
        Enemy e = new Enemy(); Player p = new Player(); p.x = e.x - Player.WIDTH;
        float old = p.x; p.x += 300; CollisionManager.resolvePlayerMovement(p, e, old, p.y);
        check(p.x == old && clear(p, e), "right sweep from touching cannot tunnel");
        p.x = e.x + Enemy.WIDTH; old = p.x; p.x -= 600;
        CollisionManager.resolvePlayerMovement(p, e, old, p.y);
        check(p.x == old && clear(p, e), "left sweep from touching cannot tunnel");
        p.x = e.x - Player.WIDTH - 100; old = p.x; p.x = 920;
        CollisionManager.resolvePlayerMovement(p, e, old, p.y);
        check(p.x + Player.WIDTH == e.x && clear(p, e), "map-wide fast crossing stops at body");
        p.x = e.x - Player.WIDTH - 20; old = e.x; e.x = 0;
        float playerX = p.x; CollisionManager.resolveEnemyMovement(e, p, old);
        check(clear(p, e) && p.x == playerX && e.x == p.x + Player.WIDTH, "left pursuing enemy cannot push or tunnel");
        e = new Enemy(); p.x = e.x + Enemy.WIDTH + 20; old = e.x; e.x = 916;
        playerX = p.x; CollisionManager.resolveEnemyMovement(e, p, old);
        check(clear(p, e) && p.x == playerX && e.x + Enemy.WIDTH == p.x, "right pursuing enemy cannot push");
        for (float enemyX : new float[]{0, 300, GameWorld.WIDTH - Enemy.WIDTH}) {
            e = new Enemy(); e.x = enemyX; p = new Player(); p.x = e.x;
            CollisionManager.resolveBodies(p, e);
            check(clear(p, e) && p.x >= 0 && p.x + Player.WIDTH <= GameWorld.WIDTH,
                    "initial overlap resolves inside arena at " + enemyX);
            old = p.x; CollisionManager.resolveBodies(p, e);
            check(p.x == old, "overlap resolution stable at " + enemyX);
        }
        e = new Enemy(); p = new Player(); p.x = 600; p.y = e.y - Player.HEIGHT;
        old = p.x; p.x = 800; CollisionManager.resolvePlayerMovement(p, e, old, p.y);
        check(p.x == 800, "crossing above enemy is allowed, touching top is not horizontal overlap");
        p.x = e.x + 5; p.y = e.y - Player.HEIGHT + 1;
        CollisionManager.resolveBodies(p, e); check(clear(p, e), "descending into enemy resolves safely to side");
        p.y = GameWorld.GROUND_Y - Player.HEIGHT; p.x = 600; old = p.x; p.x = 800;
        CollisionManager.resolvePlayerMovement(p, e, old, 0);
        check(clear(p, e), "fast descending crossing cannot leave overlapping bodies");
        p.x = 600; old = p.x; p.x = 800;
        // Horizontal entry occurs above the head, but descent intersects before horizontal exit.
        p.y = 350;
        CollisionManager.resolvePlayerMovement(p, e, old, 100);
        check(p.x == 800, "diagonal crossing that clears body before descent remains allowed");
        p.x = 800;
        CollisionManager.resolvePlayerMovement(p, e, old, 150);
        check(p.x + Player.WIDTH == e.x, "swept diagonal cannot clip through descending corner");
        e.takeDamage(e.maxHealth); p.x = 600; old = p.x; p.x = 700;
        CollisionManager.resolvePlayerMovement(p, e, old, p.y);
        check(p.x == 700, "dead enemy body does not obstruct");
        Game g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x - Player.WIDTH;
        float stopped = g.player.x;
        for (int i = 0; i < 600; i++) g.update(DT, 1, false, false);
        check(g.player.x == stopped && clear(g.player, g.enemy) && g.player.facingRight,
                "continuous contact has no jitter and preserves facing");
        g.update(DT, -1, false, false);
        check(g.player.x < stopped && !g.player.facingRight, "can move away immediately, no stuck body");
        g = new Game(); g.player.x = g.enemy.x - Player.WIDTH;
        g.update(DT, 1, false, true, true, true);
        check(g.enemy.health == 75 && clear(g.player, g.enemy), "ordinary attack works while bodies touch");
        for (int i = 0; i < 80; i++) g.update(DT, 1, false, false, true, false);
        check(g.player.health == 100 && clear(g.player, g.enemy), "block works while touching enemy");
        g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x - Player.WIDTH - 20;
        for (int i = 0; i < 120; i++) g.update(DT, 1, i == 0, false);
        check(g.player.x > g.enemy.x + Enemy.WIDTH && g.player.grounded && clear(g.player, g.enemy),
                "real jump crosses enemy and lands on other side");
        for (int rate : new int[]{30, 60, 120, 144, 240}) {
            g = new Game(); g.enemy.detectionRadius = 0;
            for (int i = 0; i < rate * 4; i++) g.update(1f / rate, 1, false, false);
            check(g.player.x + Player.WIDTH == g.enemy.x && clear(g.player, g.enemy),
                    "solid body at " + rate + " fps");
        }
        g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 2;
        g.update(10, 1, false, false);
        check(clear(g.player, g.enemy) && g.player.x + Player.WIDTH <= g.enemy.x, "frame spike cannot tunnel");
        System.out.println("Passed " + checks + " body collision checks");
    }
}
