package com.lockin.game;

/** Simulation owned exclusively by the rendering thread. No Android dependencies. */
public final class Game {
    private static final double STEP = 1.0 / 120;
    private double accumulator;
    private long restartVersion;
    public long restartVersion() { return restartVersion; }
    private boolean jumpQueued, attackQueued, dashQueued;
    public final Player player;
    public final Enemy enemy;
    public Game() { this(CombatConfig.DEFAULT); }
    public Game(CombatConfig config) { this(config, Player.DEFAULT_MAX_HEALTH, Enemy.DEFAULT_MAX_HEALTH); }
    public Game(CombatConfig config, int playerMaxHealth, int enemyMaxHealth) {
        player = new Player(config, playerMaxHealth); enemy = new Enemy(config, enemyMaxHealth);
    }
    public void restart() {
        restartVersion++;
        player.reset(); enemy.reset(); accumulator = 0; jumpQueued = attackQueued = dashQueued = false;
    }
    public void update(float dt, int move, boolean jump, boolean attack) {
        update(dt, move, jump, attack, false, false);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, boolean guardPressed) {
        update(dt, move, jump, attack, guard, guardPressed ? 1 : 0);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, int guardPresses) {
        update(dt, move, jump, attack, guard, guardPresses, false);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, int guardPresses, boolean dash) {
        if ((Float.isNaN(dt) || Float.isInfinite(dt)) || dt <= 0) return;
        if (player.defeated()) {
            if (jump) restart();
            return;
        }
        player.setGuard(guard, guardPresses);
        // Fixed simulation ticks keep contact/timing identical across render frame rates.
        // Preserve press edges when a render frame is shorter than a simulation tick.
        jumpQueued |= jump;
        attackQueued |= attack;
        dashQueued |= dash;
        accumulator += Math.min(dt, .05f);
        while (accumulator + 1e-9 >= STEP) {
            float step = (float) STEP;
            accumulator = Math.max(0, accumulator - STEP);
            CollisionManager.resolveBodies(player, enemy);
            float oldPlayerX = player.x, oldPlayerY = player.y;
            player.updateDefense(step);
            enemy.posture.update(step);
            // Eligible deathblow wins a simultaneous ATTACK/DASH press and cannot be cancelled.
            if (attackQueued && player.canDeathblow(enemy)) player.tryDeathblow(enemy);
            if (dashQueued) player.startDash(move);
            player.update(step, Math.max(-1, Math.min(1, move)), jumpQueued, attackQueued);
            player.setGuard(guard, 0);
            CollisionManager.resolvePlayerMovement(player, enemy, oldPlayerX, oldPlayerY);
            if (attackQueued) player.tryDeathblow(enemy);
            jumpQueued = attackQueued = dashQueued = false;
            // Player strikes resolve first: a lethal strike cancels the enemy attack.
            player.tryHit(enemy);
            float oldEnemyX = enemy.x;
            enemy.update(step, player);
            CollisionManager.resolveEnemyMovement(enemy, player, oldEnemyX);
            enemy.tryHit(player);
            if (player.defeated()) { accumulator = 0; break; }
        }
    }
}
