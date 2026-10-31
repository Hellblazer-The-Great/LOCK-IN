package com.lockin.game;

/** Simulation owned exclusively by the rendering thread. No Android dependencies. */
public final class Game {
    public final Player player = new Player();
    public final Enemy enemy = new Enemy();
    public void restart() { player.reset(); enemy.reset(); }
    public void update(float dt, int move, boolean jump, boolean attack) {
        if ((Float.isNaN(dt) || Float.isInfinite(dt)) || dt <= 0) return;
        if (player.defeated()) {
            if (jump) restart();
            return;
        }
        // Bound stall recovery and substep physics to avoid large collision jumps.
        float remaining = Math.min(dt, .05f);
        while (remaining > 0) {
            float step = Math.min(remaining, 1f / 120);
            player.update(step, Math.max(-1, Math.min(1, move)), jump, attack);
            // Player strikes resolve first: a lethal strike cancels the enemy attack.
            player.tryHit(enemy);
            enemy.update(step, player);
            enemy.tryHit(player);
            if (player.defeated()) break;
            jump = false;
            attack = false;
            remaining -= step;
        }
    }
}
