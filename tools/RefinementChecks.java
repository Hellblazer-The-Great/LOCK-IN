package com.lockin.game;

/** v0.2.3 deterministic combat, tuning, and bar regression checks. */
public final class RefinementChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-5, message + ": " + actual);
    }
    private static void failedTap(Player p) {
        p.setGuard(true, true); p.updateDefense(.03); p.setGuard(false, false);
    }
    private static Player close(Enemy e) {
        Player p = new Player(); p.x = e.x - Player.WIDTH - 20; return p;
    }
    public static void main(String[] args) {
        Player p = new Player();
        near(p.effectiveDeflectWindow(), .25, "initial window");
        failedTap(p); near(p.effectiveDeflectWindow(), .2, "first unsuccessful tap penalty");
        failedTap(p); near(p.effectiveDeflectWindow(), .15, "progressive tap penalty");
        for (int i = 0; i < 10; i++) failedTap(p);
        near(p.effectiveDeflectWindow(), .05, "penalty floor");
        p.setGuard(true, true); check(p.guarding(), "penalty never delays raising guard");
        p.updateDefense(.051); Enemy e = new Enemy(); p.receiveAttack(e);
        check(p.impact() == Player.Impact.BLOCK && p.health == p.maxHealth, "spammed guard remains reliable block");
        p.setGuard(false, false); p.updateDefense(.799);
        check(p.effectiveDeflectWindow() < .25, "penalty not recovered before timeout");
        p.updateDefense(.001); near(p.effectiveDeflectWindow(), .25, "penalty recovers at timeout");
        failedTap(p); failedTap(p); p.setGuard(true, true); p.updateDefense(.1); p.receiveAttack(new Enemy());
        check(p.impact() == Player.Impact.DEFLECT, "deflect within reduced window succeeds");
        p.setGuard(false, false); near(p.effectiveDeflectWindow(), .25, "successful deflect resets penalty");
        e = new Enemy(new CombatConfig(.25, 10000, 0, 25, 35, 1, 1));
        for (int i = 0; i < 12; i++) {
            p.setGuard(true, true); p.updateDefense(.2); p.receiveAttack(e);
            check(p.impact() == Player.Impact.DEFLECT, "legitimate rapid deflection " + i);
            p.setGuard(false, false); near(p.effectiveDeflectWindow(), .25, "success chain keeps full window");
        }
        p = new Player(); failedTap(p); p.setGuard(true, true); p.updateDefense(1);
        p.setGuard(true, false); p.receiveAttack(new Enemy());
        check(p.impact() == Player.Impact.BLOCK, "recovery while held cannot reopen a timed-out window");
        p.setGuard(false, false); near(p.effectiveDeflectWindow(), .25, "long held block is not an unsuccessful tap");
        p = new Player(); for (int i = 0; i < 4; i++) failedTap(p);
        p.setGuard(true, true); p.updateDefense(.04); p.receiveAttack(new Enemy());
        p.updateDefense(.1); p.receiveAttack(new Enemy());
        check(p.impact() == Player.Impact.DEFLECT, "success restores full window for a rapid follow-up hit");
        p.updateDefense(.12); p.receiveAttack(new Enemy());
        check(p.impact() == Player.Impact.BLOCK, "successful deflect does not refresh the original press timestamp");
        Controls controls = new Controls();
        for (int i = 0; i < 3; i++) { controls.setHeld(1 << Controls.GUARD); controls.setHeld(0); }
        Controls.Snapshot input = controls.consume();
        check(input.guardPresses == 3 && !input.guard, "all completed taps between frames counted");
        p = new Player(); p.setGuard(input.guard, input.guardPresses);
        near(p.effectiveDeflectWindow(), .1, "completed taps still incur penalty");
        controls.setHeld(1 << Controls.GUARD); controls.setHeld(1 << Controls.GUARD);
        check(controls.consume().guardPresses == 1, "held pointer events do not add presses");
        check(controls.consume().guardPresses == 0, "queued press count consumed once");
        controls.clear(); check(controls.consume().guardPresses == 0, "cancel clears tap queue");
        CombatConfig custom = new CombatConfig(.3, 100, 0, 25, 35, 1, .5, .025, .1, .4, .6, 75);
        p = new Player(custom); failedTap(p); near(p.effectiveDeflectWindow(), .275, "custom penalty step");
        p.updateDefense(.4); near(p.effectiveDeflectWindow(), .3, "custom penalty recovery");

        p = new Player(); e = new Enemy(); p.posture.add(99.999);
        p.setGuard(true, true); p.receiveAttack(e);
        check(!p.staggered() && p.health == 100, "deflect near maximum posture never breaks defender");
        near(p.posture.value(), 99.999, "deflect does not add defender posture");
        p.setGuard(false, false); p.setGuard(true, true); p.updateDefense(.3); p.receiveAttack(e);
        check(p.staggered() && !p.guarding() && p.impact() == Player.Impact.POSTURE_BREAK,
                "blocking at capacity causes stagger and drops guard");
        float x = p.x; p.update(DT, 1, true, true);
        check(p.x == x && !p.attacking() && p.grounded, "stagger suppresses movement jump and attack");
        p.updateDefense(.5); p.receiveAttack(e); p.updateDefense(.499);
        check(p.staggered(), "damage does not shorten stagger");
        p.setGuard(true, true); p.updateDefense(.001);
        check(!p.staggered() && p.posture.immune() && p.posture.value() == 0, "stagger expires and resets posture");
        p.setGuard(true, false); p.receiveAttack(e);
        check(p.impact() == Player.Impact.BLOCK && !p.staggered(), "held guard resumes as block, no free deflect");
        p.posture.add(1000); check(!p.staggered(), "post-break immunity prevents immediate loop");
        p.updateDefense(1); p.posture.add(100);
        check(p.staggered(), "break possible again after grace expires");
        p.reset(); check(!p.staggered() && !p.posture.immune(), "restart clears break and immunity");
        Posture meter = new Posture(custom); meter.add(100); meter.update(1);
        check(!meter.broken() && meter.immune(), "large step carries remaining immunity time correctly");
        meter.update(.1 + 1e-8); check(!meter.immune(), "remaining immunity expires at boundary");

        e = new Enemy(); p = close(e); e.deflected(100);
        check(e.state() == Enemy.State.POSTURE_BROKEN, "enemy immediately enters broken state");
        float enemyX = e.x; e.update(DT, p); e.tryHit(p);
        check(e.x == enemyX && p.health == 100, "broken enemy cannot move or attack");
        check(p.canDeathblow(e), "deathblow eligibility in range facing enemy");
        p.facingRight = false; check(!p.canDeathblow(e), "deathblow rejects wrong facing");
        p.facingRight = true; p.x = e.x - Player.WIDTH - 91;
        check(!p.canDeathblow(e), "deathblow rejects out of range");
        p.x = e.x - Player.WIDTH - 90; check(p.canDeathblow(e), "deathblow accepts exact range boundary");
        p.y = 0; check(!p.canDeathblow(e), "deathblow requires vertical overlap");
        p.y = GameWorld.GROUND_Y - Player.HEIGHT; p.posture.add(100);
        check(!p.canDeathblow(e), "staggered player cannot deathblow"); p.reset();
        p.x = e.x - Player.WIDTH - 20;
        e.posture.update(.999); check(p.canDeathblow(e), "deathblow opportunity persists before expiry");
        e.posture.update(.001); check(!p.canDeathblow(e), "deathblow unavailable at opportunity expiry");
        e.update(DT, p); check(e.state() == Enemy.State.ATTACK_RECOVERY, "expired break resumes through recovery");
        e.posture.update(1); e.deflected(100); p.facingRight = true;
        check(p.tryDeathblow(e) && e.defeated() && p.impact() == Player.Impact.DEATHBLOW, "deathblow kills standard enemy");
        check(!p.tryDeathblow(e), "deathblow cannot repeat on corpse");
        Game g = new Game(CombatConfig.DEFAULT, 100, 400);
        g.player.x = g.enemy.x - Player.WIDTH - 20; g.enemy.deflected(100);
        g.update(DT, 0, false, true);
        check(g.enemy.defeated() && g.player.impact() == Player.Impact.DEATHBLOW, "ATTACK edge invokes deathblow in game");
        g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.player.update(DT, 0, false, true); g.enemy.deflected(100);
        g.update(DT, 0, false, false);
        check(!g.enemy.defeated(), "old active attack cannot automatically become deathblow");
        g.update(DT, 0, false, true);
        check(g.enemy.defeated(), "fresh deathblow overrides ordinary attack cooldown");
        g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20;
        g.update(DT, 0, false, true); check(g.enemy.health == 75, "ordinary attack remains ordinary against unbroken enemy");
        g.enemy.takeDamage(1000); check(g.enemy.state() == Enemy.State.DEAD, "health-based defeat preserved");

        for (int maximum : new int[]{100, 400, 10000}) {
            g = new Game(CombatConfig.DEFAULT, maximum, maximum);
            near(g.player.healthFraction(), 1, "player full health " + maximum);
            near(g.enemy.healthFraction(), 1, "enemy full health " + maximum);
            g.player.health = g.enemy.health = maximum / 2;
            near(200 * g.player.healthFraction(), 100, "fixed player HUD width " + maximum);
            near(Enemy.WIDTH * g.enemy.healthFraction(), Enemy.WIDTH / 2, "fixed overhead width " + maximum);
            g.player.health = g.enemy.health = maximum * 2;
            near(g.player.healthFraction(), 1, "player overheal clamp " + maximum);
            near(g.enemy.healthFraction(), 1, "enemy overheal clamp " + maximum);
            g.player.health = g.enemy.health = -1;
            near(g.player.healthFraction(), 0, "player negative health " + maximum);
            near(g.enemy.healthFraction(), 0, "enemy negative health " + maximum);
            g.restart();
            check(g.player.health == maximum && g.enemy.health == maximum, "restart uses configured maxima " + maximum);
        }
        near(Vitals.fraction(100, 0), 0, "zero maximum safe");
        near(Vitals.fraction(100, -100), 0, "negative maximum safe");
        near(Vitals.fraction(Double.NaN, 100), 0, "non-finite HUD value safe");
        g = new Game(CombatConfig.DEFAULT, 0, 0); g.restart();
        check(g.player.defeated() && g.enemy.defeated() && g.player.healthFraction() == 0, "zero-health actors safe");
        meter = new Posture(custom); meter.add(10000); near(meter.fraction(), 1, "posture bar clamped at capacity");
        Game reference = run(120);
        for (int rate : new int[]{30, 60, 144, 240}) {
            g = run(rate);
            check(g.player.health == reference.player.health && g.enemy.health == reference.enemy.health
                    && g.enemy.state() == reference.enemy.state(), "refined combat outcome at " + rate + " fps");
            near(g.player.posture.value(), reference.player.posture.value(), "refined posture at " + rate + " fps");
        }
        System.out.println("Passed " + checks + " combat refinement checks");
    }
    private static Game run(int fps) {
        Game g = new Game(); g.player.x = g.enemy.x - Player.WIDTH - 20;
        for (int i = 0; i < fps * 10; i++) g.update(1f / fps, 0, false, false, true, i == 0);
        return g;
    }
}
