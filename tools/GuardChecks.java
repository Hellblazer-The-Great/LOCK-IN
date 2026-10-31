package com.lockin.game;

/** Guard contact and posture tests, with explicit deterministic clocks. */
public final class GuardChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-5, message + ": " + actual);
    }
    private static Player guardAt(double age) {
        Player p = new Player(); p.setGuard(true, true); p.updateDefense(age); return p;
    }
    private static void contactAt(double age, Player.Impact expected) {
        Player p = guardAt(age); Enemy e = new Enemy(); p.receiveAttack(e);
        check(p.impact() == expected, "contact at " + age + " produces " + expected);
        check(p.health == 100, "guard contact prevents health damage");
        near(p.posture.value(), expected == Player.Impact.BLOCK ? 25 : 0, "defender posture at boundary");
        near(e.posture.value(), expected == Player.Impact.DEFLECT ? 35 : 0, "attacker posture at boundary");
    }
    private static void active(Enemy e, Player p) {
        int n = 0;
        while (e.state() != Enemy.State.ATTACK_ACTIVE && n++ < 1000) e.update(DT, p);
        check(e.state() == Enemy.State.ATTACK_ACTIVE, "enemy reaches active");
    }
    private static Player close(Enemy e) {
        Player p = new Player(); p.x = e.x - Player.WIDTH - 20; return p;
    }
    public static void main(String[] args) {
        contactAt(0, Player.Impact.DEFLECT);
        contactAt(.249999, Player.Impact.DEFLECT);
        contactAt(.250, Player.Impact.DEFLECT);
        contactAt(.250001, Player.Impact.BLOCK);
        Player p = guardAt(.2); Enemy e = new Enemy();
        p.setGuard(true, false); p.updateDefense(.1); p.receiveAttack(e);
        check(p.impact() == Player.Impact.BLOCK, "held guard never refreshes window");
        p.setGuard(false, false);
        check(!p.guarding(), "release lowers guard immediately");
        p.receiveAttack(e); check(p.health == 80 && p.impact() == Player.Impact.HIT, "released guard takes health damage");
        p.setGuard(true, true); p.receiveAttack(e);
        check(p.impact() == Player.Impact.DEFLECT && p.health == 80, "fresh press resets timing");
        p.updateDefense(.3); p.setGuard(true, true); p.receiveAttack(e);
        check(p.impact() == Player.Impact.DEFLECT, "queued release/repress between frames refreshes once");
        p = new Player(); p.receiveAttack(new Enemy());
        check(p.health == 80 && p.impact() == Player.Impact.HIT, "unguarded contact damages normally");
        p.updateDefense(.26); check(p.impact() == Player.Impact.NONE, "impact feedback expires");

        e = new Enemy(); p = close(e); p.setGuard(true, true);
        e.update(DT, p); p.updateDefense(.51); active(e, p); e.tryHit(p);
        check(p.impact() == Player.Impact.BLOCK, "early guard at windup blocks at actual contact");
        double posture = p.posture.value();
        for (int i = 0; i < 10; i++) e.tryHit(p);
        near(p.posture.value(), posture, "single swing cannot repeat block posture");
        e = new Enemy(); p = close(e); active(e, p);
        p.x = 0; p.setGuard(true, true); e.tryHit(p);
        check(p.impact() == Player.Impact.NONE && e.posture.value() == 0, "activation without overlap has no effect");
        p.x = e.x - Player.WIDTH - 20; p.updateDefense(.1); e.tryHit(p);
        check(p.impact() == Player.Impact.DEFLECT, "late overlap deflects at contact within window");
        for (int i = 0; i < 10; i++) e.tryHit(p);
        near(e.posture.value(), 35, "single swing cannot repeat deflect posture");
        p.setGuard(false, false); e.tryHit(p);
        check(p.health == 100, "release after deflect cannot make same swing hit again");
        e = new Enemy(); p = close(e); active(e, p); p.x = 0; p.setGuard(true, true);
        e.tryHit(p); p.updateDefense(.251); p.x = e.x - Player.WIDTH - 20; e.tryHit(p);
        check(p.impact() == Player.Impact.BLOCK, "late overlap after deflect deadline blocks even if active started earlier");
        e = new Enemy(); p = close(e); active(e, p); p.y = 0; p.setGuard(true, true); e.tryHit(p);
        check(e.posture.value() == 0 && p.posture.value() == 0 && p.impact() == Player.Impact.NONE,
                "vertical miss cannot generate deflection or posture");
        e = new Enemy(); p = close(e); active(e, p); p.x = e.x + Enemy.WIDTH + 20;
        p.setGuard(true, true); e.tryHit(p);
        check(p.impact() == Player.Impact.NONE, "wrong attack direction cannot generate a deflection");

        CombatConfig tuning = new CombatConfig(.1, 60, 10, 15, 20, .5, .75);
        p = new Player(tuning); e = new Enemy(tuning); p.setGuard(true, true); p.updateDefense(.11);
        p.receiveAttack(e); near(p.posture.value(), 15, "configurable block posture");
        p.updateDefense(.25); near(p.posture.value(), 15, "pressure delays recovery");
        p.receiveAttack(e); near(p.posture.value(), 30, "blocks accumulate posture");
        p.updateDefense(.75); near(p.posture.value(), 27.5, "partial step recovery after pressure delay");
        p.posture.add(100); check(p.posture.broken(), "player reaches broken state");
        near(p.posture.value(), 60, "capacity clamps posture");
        p.receiveAttack(e);
        check(p.health == 80 && p.impact() == Player.Impact.HIT && !p.guarding(),
                "Phase 2C stagger temporarily removes guard protection");
        p.posture.update(.5); p.posture.add(10); p.posture.update(.25);
        check(!p.posture.broken() && p.posture.value() == 0, "temporary break ends and repeated pressure does not extend it");
        p.setGuard(false, false); p.setGuard(true, true); p.receiveAttack(e);
        near(e.posture.value(), 20, "configurable deflect posture");
        p.receiveAttack(e); p.receiveAttack(e);
        check(e.posture.broken(), "enemy deflections accumulate to break");
        e.posture.update(.75); check(!e.posture.broken(), "enemy break expires");
        Posture meter = new Posture(CombatConfig.DEFAULT); meter.add(25);
        meter.update(.9); near(meter.value(), 25, "default recovery quiet delay");
        meter.pressure(); meter.update(.9); near(meter.value(), 25, "contact renews pressure delay");
        meter.update(.6); near(meter.value(), 19, "gradual recovery");
        meter.update(100); near(meter.value(), 0, "recovery clamps at zero");
        meter.add(10); meter.update(Double.NaN); meter.update(-1); near(meter.value(), 10, "invalid dt ignored");
        boolean rejected = false;
        try { new CombatConfig(.25, 0, 1, 1, 1, 1, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid tuning rejected");

        Game g = new Game(); g.player.setGuard(true, true); g.player.posture.add(100);
        g.enemy.posture.add(100); g.player.takeDamage(100);
        g.update(DT, 0, true, false, true, false);
        check(g.player.health == 100 && !g.player.guarding() && g.player.posture.value() == 0
                && !g.player.posture.broken() && g.enemy.posture.value() == 0 && !g.enemy.posture.broken(),
                "defeat/restart clears defense and both posture meters");
        g = new Game(); g.player.posture.add(25);
        g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.player.updateDefense(.9);
        g.player.update(DT, 0, false, true); g.player.tryHit(g.enemy);
        g.player.updateDefense(.9);
        near(g.player.posture.value(), 25, "landed player strikes also renew pressure delay");
        // Same input schedule in seconds at 30/60/120 fps, including actual AI contact.
        Game a = run(30), b = run(60), c = run(120);
        check(a.player.health == 100 && b.player.health == 100 && c.player.health == 100,
                "held guard protects at every tested frame rate");
        near(a.player.posture.value(), b.player.posture.value(), "30/60 fps defender posture");
        near(b.player.posture.value(), c.player.posture.value(), "60/120 fps defender posture");
        near(a.enemy.posture.value(), c.enemy.posture.value(), "30/120 fps attacker posture");
        check(a.enemy.state() == c.enemy.state(), "AI timing unchanged across frame rates");
        for (int rate : new int[]{144, 240}) {
            Game other = run(rate);
            near(other.player.posture.value(), c.player.posture.value(), "fixed combat ticks at " + rate + " fps");
            near(other.enemy.posture.value(), c.enemy.posture.value(), "fixed enemy posture at " + rate + " fps");
            check(other.player.health == c.player.health && other.enemy.state() == c.enemy.state(),
                    "same combat outcome at " + rate + " fps");
        }
        g = new Game();
        g.update(1f / 240, 0, true, true, true, true);
        check(g.player.guarding(), "guard raises even before a full simulation tick");
        g.update(1f / 240, 0, false, false, true, false);
        check(!g.player.grounded && g.player.attacking(), "sub-tick jump and attack edges are preserved");
        for (int rate : new int[]{30, 60, 120, 144}) {
            Posture m = new Posture(tuning); m.add(40);
            for (int i = 0; i < rate * 2; i++) m.update(1.0 / rate);
            near(m.value(), 25, "posture recovery independent of frame rate " + rate);
        }
        System.out.println("Passed " + checks + " guard/posture checks");
    }
    private static Game run(int fps) {
        Game g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20;
        for (int i = 0; i < fps * 5; i++) g.update(1f / fps, 0, false, false, true, i == 0);
        return g;
    }
}
