package com.lockin.game;

/** Simulation owned exclusively by the rendering thread. No Android dependencies. */
public final class Game {
    private static final double STEP = 1.0 / 120;
    private double accumulator;
    private boolean jumpQueued, attackQueued;
    public final Player player;
    public final Enemy enemy;
    public Game() { this(CombatConfig.DEFAULT); }
    public Game(CombatConfig config) { player = new Player(config); enemy = new Enemy(config); }
    public void restart() {
        player.reset(); enemy.reset(); accumulator = 0; jumpQueued = attackQueued = false;
    }
    public void update(float dt, int move, boolean jump, boolean attack) {
        update(dt, move, jump, attack, false, false);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, boolean guardPressed) {
        if ((Float.isNaN(dt) || Float.isInfinite(dt)) || dt <= 0) return;
        if (player.defeated()) {
            if (jump) restart();
            return;
        }
        player.setGuard(guard, guardPressed);
        // Fixed simulation ticks keep contact/timing identical across render frame rates.
        // Preserve press edges when a render frame is shorter than a simulation tick.
        jumpQueued |= jump;
        attackQueued |= attack;
        accumulator += Math.min(dt, .05f);
        while (accumulator + 1e-9 >= STEP) {
            float step = (float) STEP;
            accumulator = Math.max(0, accumulator - STEP);
            player.updateDefense(step);
            enemy.posture.update(step);
            player.update(step, Math.max(-1, Math.min(1, move)), jumpQueued, attackQueued);
            jumpQueued = attackQueued = false;
            // Player strikes resolve first: a lethal strike cancels the enemy attack.
            player.tryHit(enemy);
            enemy.update(step, player);
            enemy.tryHit(player);
            if (player.defeated()) { accumulator = 0; break; }
        }
    }
}
