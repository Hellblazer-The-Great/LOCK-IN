package com.lockin.game;

public final class MovesetChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static final CombatConfig TEST = new CombatConfig(.25, 10000, 0, 25, 35, 1, 1);
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .001, message + ": " + actual);
    }
    private static Player close(Enemy e) {
        Player p = new Player(TEST, 10000); p.x = e.x - Player.WIDTH - 20; return p;
    }
    private static void active(Enemy e, Player p, int strike) {
        int ticks = 0;
        while (!(e.state() == Enemy.State.ATTACK_ACTIVE && e.strikeNumber() == strike) && ticks++ < 3000) e.update(DT, p);
        check(e.state() == Enemy.State.ATTACK_ACTIVE && e.strikeNumber() == strike, "reaches strike " + strike + " of " + e.move());
    }
    private static Enemy prepare(EnemyMove move) {
        Enemy e = new Enemy(TEST, 10000); Player p = close(e);
        int ticks = 0;
        while (!(e.move() == move && e.state() == Enemy.State.ATTACK_WINDUP) && ticks++ < 3000) e.update(DT, p);
        check(e.move() == move && e.state() == Enemy.State.ATTACK_WINDUP, "prepares " + move);
        return e;
    }
    private static void resolve(Enemy e, Player p) {
        int ticks = 0;
        while (e.state() != Enemy.State.IDLE && ticks++ < 3000) e.update(DT, p);
        check(e.state() == Enemy.State.IDLE, "move completes");
    }
    public static void main(String[] args) {
        Enemy e = new Enemy(TEST, 10000); Player p = close(e);
        EnemyMove[] order = {EnemyMove.NORMAL_SLASH, EnemyMove.DOUBLE_SLASH, EnemyMove.HEAVY_OVERHEAD,
                EnemyMove.NORMAL_SLASH, EnemyMove.DOUBLE_SLASH, EnemyMove.HEAVY_OVERHEAD};
        for (EnemyMove expected : order) {
            int n = 0;
            while (e.state() != Enemy.State.ATTACK_WINDUP && n++ < 3000) e.update(DT, p);
            check(e.move() == expected, "fixed move order " + expected);
            p.x = 0; resolve(e, p); // A complete miss must still advance the sequence.
            float oldX = e.x;
            for (int i = 0; i < 30; i++) e.update(DT, p);
            check(e.state() == Enemy.State.IDLE && e.x == oldX, "no teleport outside detection range");
            p.x = e.x - Player.WIDTH - 100;
            e.update(DT, p); check(e.state() == Enemy.State.CHASE, "temporary chase does not reset sequence");
            p.x = e.x - Player.WIDTH - 20;
        }
        e.reset(); check(e.nextMove() == EnemyMove.NORMAL_SLASH && e.strikeNumber() == 1, "restart resets move sequence");
        e = prepare(EnemyMove.HEAVY_OVERHEAD); e.takeDamage(10000);
        check(e.state() == Enemy.State.DEAD && e.nextMove() == EnemyMove.NORMAL_SLASH, "death resets sequence");
        e.update(DT, new Player()); check(e.state() == Enemy.State.DEAD, "death cannot restart attack");

        for (EnemyMove move : EnemyMove.values()) {
            e = prepare(move); p = close(e); active(e, p, 1);
            int health = p.health;
            e.tryHit(p);
            check(p.health == health - 20 * move.damageMultiplier, "unblocked health damage " + move);
            near(p.posture.value(), 25 * move.damageMultiplier, "unblocked posture damage " + move);
            for (int i = 0; i < 10; i++) e.tryHit(p);
            check(p.health == health - 20 * move.damageMultiplier, "health applied once per strike " + move);
            near(p.posture.value(), 25 * move.damageMultiplier, "posture applied once per strike " + move);
            e = prepare(move); p = close(e); active(e, p, 1);
            p.setGuard(true, true); p.updateDefense(.3); e.tryHit(p);
            check(p.health == 10000 && p.impact() == Player.Impact.BLOCK, "block prevents health damage " + move);
            near(p.posture.value(), 25 * move.damageMultiplier, "blocked posture damage " + move);
            e = prepare(move); p = close(e); active(e, p, 1); p.posture.add(9999);
            p.setGuard(true, true); e.tryHit(p);
            check(p.health == 10000 && p.impact() == Player.Impact.DEFLECT && !p.staggered(), "deflection preserves defender " + move);
            near(e.posture.value(), 35 * move.damageMultiplier, "deflected posture transfer " + move);
            near(p.posture.value(), 9999, "deflection adds no defender posture " + move);
            e = prepare(move); p = close(e); active(e, p, 1);
            p.startDash(0); p.update(.04f, 0, false, false); p.x = e.x - Player.WIDTH - 20;
            e.tryHit(p); check(p.health == 10000 && p.posture.value() == 0 && e.posture.value() == 0, "iframe contact has no combat effects " + move);
            p.update(.11f, 0, false, false); p.x = e.x - Player.WIDTH - 20; e.tryHit(p);
            check(p.health == 10000 && p.posture.value() == 0, "dodged strike stays consumed outside iframes " + move);
            e = prepare(move); p = close(e); active(e, p, 1);
            p.x = e.x - Player.WIDTH - move.reach; e.tryHit(p);
            check(p.health == 10000, "hitbox exact edge is a miss " + move);
            p.x += .1f; e.tryHit(p);
            check(p.health < 10000, "actual overlap needed for contact " + move);
            e = prepare(move); p = close(e); active(e, p, 1);
            p.x = e.x + Enemy.WIDTH + 10; e.tryHit(p);
            check(p.health == 10000, "forward hitbox cannot hit behind attacker " + move);
            // Mirror the committed facing and evaluate the mirrored hitbox.
            e.facingRight = true; e.tryHit(p);
            check(p.health == 10000 - 20 * move.damageMultiplier, "right-facing hitbox " + move);
            near(e.attackRight() - e.attackLeft(), move.reach, "configurable reach " + move);
            near(e.attackBottom() - e.attackTop(), move.hitboxHeight, "configurable hitbox height " + move);
        }
        for (EnemyMove move : EnemyMove.values()) {
            e = prepare(move); p = close(e); active(e, p, 1);
            p.y = e.y - Player.HEIGHT - 5; e.tryHit(p);
            check((p.health < 10000) == (move == EnemyMove.HEAVY_OVERHEAD), "heavy alone reaches elevated target " + move);
        }
        check(EnemyMove.HEAVY_OVERHEAD.reach < EnemyMove.NORMAL_SLASH.reach
                && EnemyMove.DOUBLE_SLASH.reach < EnemyMove.NORMAL_SLASH.reach,
                "heavy and double have shorter horizontal reach");
        check(EnemyMove.HEAVY_OVERHEAD.windup > EnemyMove.NORMAL_SLASH.windup
                && EnemyMove.HEAVY_OVERHEAD.recovery > EnemyMove.NORMAL_SLASH.recovery, "heavy has longer readable windup and recovery");

        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1); e.tryHit(p);
        active(e, p, 2); e.tryHit(p); e.tryHit(p);
        check(p.health == 9960 && p.posture.value() == 50, "double slash has two independent damage events");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1);
        p.x = 0; e.tryHit(p); active(e, p, 2); p.x = e.x - Player.WIDTH - 20; e.tryHit(p);
        check(p.health == 9980 && p.posture.value() == 25, "missed first double strike does not suppress second");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1); e.tryHit(p);
        active(e, p, 2); p.x = 0; e.tryHit(p); check(p.health == 9980, "escaped second double strike misses naturally");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1);
        p.setGuard(true, true); e.tryHit(p); p.setGuard(false, false);
        active(e, p, 2); p.setGuard(true, true); e.tryHit(p);
        check(p.health == 10000 && p.posture.value() == 0 && e.posture.value() == 70,
                "both double slash strikes independently deflect");
        p.setGuard(false, false); near(p.effectiveDeflectWindow(), .25, "legitimate double deflections do not trigger anti-spam");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1);
        p.setGuard(true, true); p.updateDefense(.3); e.tryHit(p); active(e, p, 2); e.tryHit(p);
        check(p.health == 10000 && p.posture.value() == 50, "held block handles both double strikes");
        e = prepare(EnemyMove.HEAVY_OVERHEAD); p = new Player(); p.x = e.x - Player.WIDTH - 20;
        p.posture.add(60); active(e, p, 1); p.setGuard(true, true); p.updateDefense(.3); e.tryHit(p);
        check(p.health == 100 && p.staggered(), "heavy block triggers posture break through shared rules");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = new Player(); p.x = e.x - Player.WIDTH - 20;
        active(e, p, 1); e.posture.add(9980); p.setGuard(true, true); e.tryHit(p);
        check(e.state() == Enemy.State.POSTURE_BROKEN && e.nextMove() == EnemyMove.HEAVY_OVERHEAD,
                "deflected first double strike breaks posture and resolves interrupted move once");
        for (int i = 0; i < 200; i++) { e.update(DT, p); e.tryHit(p); }
        check(p.health == 100 && e.nextMove() == EnemyMove.HEAVY_OVERHEAD,
                "posture break cancels second strike without repeatedly advancing sequence");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1);
        while (e.state() == Enemy.State.ATTACK_ACTIVE) e.update(DT, p);
        int separation = 0;
        while (e.state() != Enemy.State.ATTACK_ACTIVE && separation++ < 1000) e.update(DT, p);
        check(separation * DT >= EnemyMove.DOUBLE_SLASH.betweenStrikes
                && separation * DT <= EnemyMove.DOUBLE_SLASH.betweenStrikes + DT * 1.1,
                "double slash has a clear configured separation between active strikes");
        e = prepare(EnemyMove.DOUBLE_SLASH); p = close(e); active(e, p, 1); e.takeDamage(10000);
        activeDeadContact(e, p); check(p.health == 10000, "enemy death cancels remaining double strike");
        e = new Enemy(); p = new Player(); p.x = e.x - Player.WIDTH - 20;
        p.update(DT, 0, false, true); p.tryHit(e); p.tryHit(e);
        check(e.health == 75 && e.posture.value() == 25, "player ordinary strike commits health and posture once");
        e = new Enemy(); e.posture.add(90); p = new Player(); p.x = e.x - Player.WIDTH - 20;
        p.update(DT, 0, false, true); p.tryHit(e);
        check(e.state() == Enemy.State.POSTURE_BROKEN && p.canDeathblow(e), "player health hit can posture-break enemy and expose deathblow");
        p.tryDeathblow(e); double committed = e.posture.value(); e.receiveHit(25, 25); e.deflected(35); p.receiveAttack(e);
        check(e.health == 0 && e.posture.value() == committed && p.health == 100, "no extra health/posture damage after death");
        e = new Enemy(); e.health = 1; e.receiveHit(25, 25);
        check(e.defeated() && e.posture.value() == 25, "lethal health strike still commits its simultaneous posture component");
        e.receiveHit(25, 25); check(e.posture.value() == 25, "subsequent strike on corpse commits nothing");
        e = prepare(EnemyMove.HEAVY_OVERHEAD); p = close(e); p.health = 1; active(e, p, 1); e.tryHit(p);
        check(p.defeated() && p.posture.value() == 50, "lethal heavy hit commits health and posture once");
        for (int fps : new int[]{30, 60, 120, 144, 240}) {
            Game g = run(fps), reference = run(120);
            check(g.player.health == reference.player.health && g.enemy.state() == reference.enemy.state()
                    && g.enemy.move() == reference.enemy.move() && g.enemy.strikeNumber() == reference.enemy.strikeNumber(),
                    "moveset deterministic at " + fps + " fps");
            near(g.player.posture.value(), reference.player.posture.value(), "posture deterministic at " + fps + " fps");
        }
        System.out.println("Passed " + checks + " moveset checks");
    }
    private static void activeDeadContact(Enemy e, Player p) {
        for (int i = 0; i < 240; i++) { e.update(DT, p); e.tryHit(p); }
    }
    private static Game run(int fps) {
        Game g = new Game(TEST, 10000, 10000); g.player.x = g.enemy.x - Player.WIDTH - 20;
        for (int i = 0; i < fps * 12; i++) g.update(1f / fps, 0, false, false);
        return g;
    }
}
