package com.lockin.game;

/** Deterministic combat checks using a fixed simulation clock and no Android runtime. */
public final class EnemyChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void tick(Enemy e, Player p) { e.update(DT, p); e.tryHit(p); }
    private static void ticks(Enemy e, Player p, int n) { for (int i = 0; i < n; i++) tick(e, p); }
    private static int until(Enemy e, Player p, Enemy.State state) {
        int n = 0;
        while (e.state() != state && n < 1000) { tick(e, p); n++; }
        check(e.state() == state, "reaches " + state);
        return n;
    }
    private static Player leftOf(Enemy e) {
        Player p = new Player(); p.x = e.x - Player.WIDTH - 20; return p;
    }
    public static void main(String[] args) {
        Enemy e = new Enemy(); Player p = new Player();
        tick(e, p);
        check(e.state() == Enemy.State.IDLE && e.x == 670, "idle outside radius");
        e.detectionRadius = 50;
        p.x = e.x - 140;
        check(!e.detects(p), "configurable detection radius");
        e.detectionRadius = 200;
        check(e.detects(p), "inside detection radius");
        p.y = -500;
        check(!e.detects(p), "detection includes vertical distance");
        p.y = GameWorld.GROUND_Y - Player.HEIGHT;
        tick(e, p);
        check(e.state() == Enemy.State.CHASE && e.x < 670 && !e.facingRight, "chase left");
        until(e, p, Enemy.State.ATTACK_WINDUP);
        float gap = e.x - (p.x + Player.WIDTH), stopped = e.x;
        check(Math.abs(gap - Enemy.MELEE_DISTANCE) < .01f, "stops at melee gap without overshoot");
        int startHealth = p.health;
        ticks(e, p, 30);
        check(e.state() == Enemy.State.ATTACK_WINDUP && p.health == startHealth && e.x == stopped,
                "windup telegraphs without moving or dealing damage");
        int rest = until(e, p, Enemy.State.ATTACK_ACTIVE);
        check(Math.abs((30 + rest) * DT - Enemy.WINDUP) <= DT * 1.1f, "windup duration");
        check(p.health == 80, "active overlap deals damage");
        ticks(e, p, 10);
        check(p.health == 80, "one hit per active swing");
        int activeRest = until(e, p, Enemy.State.ATTACK_RECOVERY);
        check(Math.abs((10 + activeRest) * DT - Enemy.ACTIVE) <= DT * 1.1f, "active duration");
        ticks(e, p, 40);
        check(e.state() == Enemy.State.ATTACK_RECOVERY && p.health == 80, "recovery prevents immediate attack");
        int recoveryRest = until(e, p, Enemy.State.IDLE);
        check(Math.abs((40 + recoveryRest) * DT - Enemy.RECOVERY) <= DT * 1.1f, "recovery duration");
        until(e, p, Enemy.State.ATTACK_ACTIVE);
        check(p.health == 60, "next swing can hit again after cooldown");

        e = new Enemy(); p = leftOf(e); tick(e, p);
        p.x = e.x + Enemy.WIDTH + 20;
        until(e, p, Enemy.State.ATTACK_ACTIVE);
        check(!e.facingRight && p.health == 100, "attack facing locks through windup; crossing dodges hitbox");
        until(e, p, Enemy.State.IDLE); tick(e, p);
        check(e.facingRight, "faces right again after recovery");
        until(e, p, Enemy.State.ATTACK_ACTIVE);
        check(p.health == 80 && e.attackLeft() == e.x + Enemy.WIDTH, "right directional hitbox");

        e = new Enemy(); p = leftOf(e); tick(e, p); p.y = 0;
        until(e, p, Enemy.State.ATTACK_ACTIVE);
        check(p.health == 100, "vertical hitbox miss");
        p.y = GameWorld.GROUND_Y - Player.HEIGHT; tick(e, p);
        check(p.health == 80, "entering hitbox during active period can be hit");
        e = new Enemy(); p = leftOf(e); tick(e, p); p.x = 0;
        until(e, p, Enemy.State.ATTACK_ACTIVE);
        check(p.health == 100, "out of range active attack misses");
        until(e, p, Enemy.State.ATTACK_RECOVERY); p.x = e.x - Player.WIDTH - 20;
        tick(e, p); check(p.health == 100, "recovery hitbox cannot damage");

        e = new Enemy(); p = leftOf(e); tick(e, p); e.takeDamage(100);
        ticks(e, p, 200);
        check(e.state() == Enemy.State.DEAD && p.health == 100, "death cancels pending attack");
        e.takeDamage(-10); check(e.health == 0, "dead enemy cannot heal from negative damage");
        p.takeDamage(-10); check(p.health == 100, "negative player damage ignored");
        p.takeDamage(1000); check(p.defeated() && p.health == 0, "player health clamps at zero");

        Game g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20;
        for (int i = 0; i < 1500 && !g.player.defeated(); i++) g.update(DT, 0, false, false);
        check(g.player.defeated(), "enemy can defeat player through game coordination");
        float deadX = g.player.x, enemyX = g.enemy.x;
        g.update(DT, 1, false, true);
        check(g.player.x == deadX && g.enemy.x == enemyX && !g.player.attacking(), "defeat freezes combat and movement");
        g.update(DT, 0, true, true);
        check(g.player.health == 100 && g.enemy.health == 100 && g.player.x == 180 && g.enemy.x == 670
                && g.enemy.state() == Enemy.State.IDLE && !g.player.attacking(), "jump restart resets all combat state");
        g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.update(DT, 0, false, true);
        check(g.enemy.health == 75, "restart clears player attack cooldown");
        g.enemy.takeDamage(75);
        for (int i = 0; i < 120; i++) g.update(DT, 0, false, false);
        check(g.player.health == 100 && g.enemy.state() == Enemy.State.DEAD, "defeated enemy never attacks");

        g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.enemy.takeDamage(75);
        int windupTicks = 0;
        while (g.enemy.state() != Enemy.State.ATTACK_ACTIVE && windupTicks++ < 1000) {
            // Time enemy directly so a simultaneous lethal strike can be tested without prior damage.
            g.enemy.update(DT, g.player);
        }
        check(g.enemy.state() == Enemy.State.ATTACK_ACTIVE, "simultaneous strike setup reaches active");
        g.update(DT, 0, false, true);
        check(g.enemy.defeated() && g.player.health == 100, "lethal player strike cancels simultaneous enemy hit");
        e = new Enemy(); p = new Player(); p.x = 850; tick(e, p);
        check(e.state() == Enemy.State.CHASE && e.facingRight && e.x > 670, "chase right");
        until(e, p, Enemy.State.ATTACK_WINDUP);
        check(Math.abs(p.x - (e.x + Enemy.WIDTH) - Enemy.MELEE_DISTANCE) < .01f,
                "right chase stops and begins attack despite floating point rounding");
        e = new Enemy(); p.x = 850; tick(e, p);
        p.x = 0; tick(e, p);
        check(e.state() == Enemy.State.IDLE, "losing detection stops chase");
        float x = e.x; e.update(Float.NaN, p); e.update(-1, p);
        check(e.x == x, "invalid enemy delta ignored");
        Game a = new Game(), b = new Game();
        a.player.x = b.player.x = 500;
        for (int i = 0; i < 180; i++) a.update(1f / 60, 0, false, false);
        for (int i = 0; i < 360; i++) b.update(DT, 0, false, false);
        check(Math.abs(a.enemy.x - b.enemy.x) < .02f && a.player.health == b.player.health
                && a.enemy.state() == b.enemy.state(), "combat consistent at 60 and 120 fps");
        System.out.println("Passed " + checks + " enemy combat checks");
    }
}
