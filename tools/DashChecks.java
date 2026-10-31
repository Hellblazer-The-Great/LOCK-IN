package com.lockin.game;

public final class DashChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .002, message + ": " + actual);
    }
    private static Player dashAt(float age) {
        Player p = new Player(); p.startDash(1);
        if (age > 0) p.update(age, 0, false, false);
        return p;
    }
    public static void main(String[] args) {
        Player p = new Player(); float x = p.x;
        check(p.startDash(1) && p.dashDirection() == 1, "held right selects right dash");
        p.update(.18f, 0, false, false);
        near(p.x - x, Player.DASH_SPEED * Player.DASH_DURATION, "exact short dash distance");
        check(!p.dashing(), "dash movement ends at duration");
        check(!p.startDash(-1), "cooldown remains after movement ends");
        p.update(.269f, 0, false, false); check(!p.startDash(1), "cooldown rejects before 450 ms");
        p.update(.001f, 0, false, false); check(p.startDash(-1), "cooldown measured from initiation");
        check(!p.startDash(1), "no recursive dash restart");
        p = new Player(); p.startDash(-1); check(p.dashDirection() == -1 && !p.facingRight, "held left dash and facing");
        p = new Player(); p.startDash(0); check(p.dashDirection() == -1 && p.facingRight, "neutral backward dash preserves right facing");
        p = new Player(); p.facingRight = false; p.startDash(0);
        check(p.dashDirection() == 1 && !p.facingRight, "neutral backward dash preserves left facing");
        p.update(.04f, -1, false, false); check(p.dashDirection() == 1, "direction remains committed during dash");
        p = new Player(); p.x = 2; p.startDash(-1); p.update(.18f, 0, false, false);
        check(p.x == 0 && p.grounded, "left boundary and ground preserved");
        p = new Player(); p.x = GameWorld.WIDTH - Player.WIDTH - 2; p.startDash(1); p.update(.18f, 0, false, false);
        check(p.x == GameWorld.WIDTH - Player.WIDTH, "right boundary preserved");
        float[] ages = {0, .039999f, .04f, .040001f, .139999f, .14f, .140001f, .18f};
        boolean[] invincible = {false, false, true, true, true, false, false, false};
        for (int i = 0; i < ages.length; i++) {
            p = dashAt(ages[i]);
            check(p.invulnerable() == invincible[i], "iframe boundary " + ages[i]);
            p.receiveAttack(new Enemy());
            check(p.health == (invincible[i] ? 100 : 80), "health at iframe boundary " + ages[i]);
            near(p.posture.value(), invincible[i] ? 0 : Enemy.POSTURE_DAMAGE, "posture at iframe boundary " + ages[i]);
            check(p.impact() != Player.Impact.DEFLECT && p.impact() != Player.Impact.BLOCK, "dash dodge is not guard outcome");
        }
        Enemy e = new Enemy(); p = new Player(); p.x = e.x - Player.WIDTH - 20;
        p.update(DT, 0, false, true); check(p.attacking(), "ordinary attack starts");
        p.startDash(-1); p.tryHit(e);
        check(e.health == 100 && e.posture.value() == 0 && !p.attacking(), "dash cancels unconnected active attack");
        p.update(.18f, 0, false, true); p.tryHit(e);
        check(!p.attacking() && e.health == 100, "attack edge during dash cannot reactivate cancelled hitbox");
        p.update(.4f, 0, false, false); p.tryHit(e);
        check(e.health == 100, "cancelled attack never returns after cooldown");
        p = new Player(); e = new Enemy(); p.x = e.x - Player.WIDTH - 20;
        p.update(DT, 0, false, true); p.tryHit(e); p.startDash(-1); p.tryHit(e);
        check(e.health == 75 && e.posture.value() == 25, "cancel after contact preserves committed health and posture");
        p = new Player(); p.update(DT, 0, false, true); p.update(.17f, 0, false, false);
        check(!p.attacking() && p.startDash(1), "dash cancels ordinary attack recovery");
        p = new Player(); p.setGuard(true, true); p.startDash(1); p.setGuard(true, true);
        check(!p.guarding(), "guard suppressed during dash");
        p.update(.18f, 0, false, false); p.setGuard(true, false); p.receiveAttack(new Enemy());
        check(p.impact() == Player.Impact.BLOCK, "held guard resumes as block without free deflect window");
        p.setGuard(false, false); p.setGuard(true, true); p.receiveAttack(new Enemy());
        check(p.impact() == Player.Impact.DEFLECT, "fresh post-dash guard edge can deflect");

        p = new Player(); p.takeDamage(20); p.posture.add(100);
        check(p.staggered() && p.startDash(-1) && !p.staggered(), "dash cancels posture stagger");
        check(p.health == 80 && p.posture.value() == 100 && p.posture.immune(), "stagger cancel does not refund committed damage");
        double cooldown = p.dashCooldownRemaining(); p.posture.add(1000);
        check(!p.startDash(1) && p.dashCooldownRemaining() == cooldown, "stagger escape cannot reset dash cooldown");
        p.update(.18f, 0, false, false);
        check(!p.invulnerable() && !p.startDash(1), "no lingering iframe or early repeat dash");
        p.reset(); check(!p.dashing() && !p.invulnerable() && p.dashCooldownRemaining() == 0, "restart clears dash state");
        p.takeDamage(100); check(!p.startDash(1), "dead player cannot dash");
        p = new Player(); e = new Enemy(); p.x = e.x - Player.WIDTH - 20; e.deflected(100);
        check(p.tryDeathblow(e) && p.deathblowing() && !p.startDash(1), "deathblow execution cannot be dash cancelled");
        p.update(.299f, 1, true, true); check(!p.startDash(1) && p.grounded && !p.attacking(), "execution lock prevents action cancellation");
        p.update(.001f, 0, false, false); check(p.startDash(1), "dash available after deathblow execution ends");

        Game g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x - Player.WIDTH - 20;
        for (int i = 0; i < 30; i++) g.update(DT, 1, false, false, false, 0, i == 0);
        check(g.player.x + Player.WIDTH == g.enemy.x, "dash cannot pass through solid enemy even with iframes");
        g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x + Enemy.WIDTH + 20;
        for (int i = 0; i < 30; i++) g.update(DT, -1, false, false, false, 0, i == 0);
        check(g.player.x == g.enemy.x + Enemy.WIDTH, "leftward dash respects solid enemy");
        g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.player.y = 220; g.player.grounded = false;
        for (int i = 0; i < 24; i++) g.update(DT, 1, false, false, false, 0, i == 0);
        check(g.player.x >= g.enemy.x + Enemy.WIDTH, "air dash crosses above non-overlapping enemy body");
        g = new Game(); g.enemy.detectionRadius = 0;
        g.update(1f / 240, 0, false, false, false, 0, true);
        g.update(1f / 240, 0, false, false, false, 0, false);
        check(g.player.dashing(), "dash edge retained across sub-tick render frames");
        g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20; g.enemy.deflected(100);
        g.update(DT, 0, false, true, false, 0, true);
        check(g.enemy.defeated() && g.player.deathblowing() && !g.player.dashing(), "simultaneous eligible deathblow takes priority over dash");
        g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.update(DT, 0, false, true, false, 0, true);
        check(g.player.dashing() && g.enemy.health == 100 && g.enemy.posture.value() == 0,
                "game resolves dash before an unconnected simultaneous ordinary attack");
        g = new Game(); g.enemy.detectionRadius = 0; g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.update(DT, 0, false, true); g.update(DT, 0, false, false, false, 0, true);
        check(g.enemy.health == 75 && g.enemy.posture.value() == 25 && g.player.dashing(),
                "game cancellation preserves a previous tick's committed hit");
        g = new Game(); g.player.takeDamage(20); g.player.posture.add(100);
        g.update(DT, 0, false, false, false, 0, true);
        check(g.player.dashing() && !g.player.staggered() && g.player.health == 80 && g.player.posture.value() == 100,
                "fixed-step dash escapes stagger without undoing damage");
        Game reference = run(120);
        for (int fps : new int[]{30, 60, 144, 240}) {
            g = run(fps);
            near(g.player.x, reference.player.x, "dash movement at " + fps + " fps");
            check(g.player.health == reference.player.health && g.player.dashing() == reference.player.dashing(), "dash outcome at " + fps + " fps");
            near(g.player.dashCooldownRemaining(), reference.player.dashCooldownRemaining(), "dash cooldown at " + fps + " fps");
        }
        Controls controls = new Controls(); int held = (1 << Controls.LEFT) | (1 << Controls.GUARD) | (1 << Controls.DASH);
        controls.setHeld(held); Controls.Snapshot s = new Controls.Snapshot(); controls.consume(s);
        check(s.dash && s.guard && s.move == -1, "dash combines with multitouch movement and guard");
        controls.setHeld(held); controls.consume(s); check(!s.dash && s.guard, "holding DASH cannot retrigger");
        controls.setHeld(held & ~(1 << Controls.DASH)); controls.consume(s);
        check(!s.dash && s.guard && s.move == -1, "lifting DASH preserves other fingers");
        controls.clear(); controls.consume(s); check(!s.dash && !s.guard && s.move == 0, "cancel clears dash and other inputs");
        System.out.println("Passed " + checks + " dash checks");
    }
    private static Game run(int fps) {
        Game g = new Game(); g.enemy.detectionRadius = 0;
        for (int i = 0; i < fps; i++) g.update(1f / fps, 0, false, false, false, 0, i == 0);
        return g;
    }
}
