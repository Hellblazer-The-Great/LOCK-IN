package com.lockin.game;

public final class Player {
    public static final float WIDTH = 36, HEIGHT = 64, SPEED = 250;
    public static final float ATTACK_RANGE = 68, ATTACK_DURATION = .16f, COOLDOWN = .45f;
    public float x = 180, y = GameWorld.GROUND_Y - HEIGHT;
    public float velocityY;
    public static final int DEFAULT_MAX_HEALTH = 100;
    public final int maxHealth;
    public int health;
    public boolean facingRight = true, grounded = true;
    public enum Impact { NONE, DEFLECT, BLOCK, HIT, POSTURE_BREAK, DEATHBLOW }
    public final Posture posture;
    private final CombatConfig config;
    private boolean guarding, guardSucceeded;
    private int failedTaps;
    private double tapQuietTime, guardWindow;
    private double guardAge, feedbackTime;
    private Impact impact = Impact.NONE;
    private float attackTime, cooldown;
    private boolean hitThisAttack;

    public Player() { this(CombatConfig.DEFAULT); }
    public Player(CombatConfig config) { this(config, DEFAULT_MAX_HEALTH); }
    public Player(CombatConfig config, int maxHealth) {
        this.config = config; this.maxHealth = Math.max(0, maxHealth);
        posture = new Posture(config); reset();
    }
    public float healthFraction() { return Vitals.fraction(health, maxHealth); }
    public boolean staggered() { return posture.broken(); }
    public double effectiveDeflectWindow() {
        return guarding ? guardWindow : Math.max(config.minimumDeflectWindow, config.deflectWindow - failedTaps * config.spamStep);
    }
    public boolean guarding() { return guarding && !staggered() && !defeated(); }
    public Impact impact() { return impact; }
    private void failTaps(int count) {
        if (count <= 0) return;
        failedTaps = Math.min(1000000, failedTaps + Math.min(count, 1000000));
        tapQuietTime = 0;
    }
    private void finishGuardAttempt() {
        if (guarding && !guardSucceeded && guardAge < config.spamRecovery) {
            failTaps(1);
        }
    }
    public void setGuard(boolean held, boolean pressed) { setGuard(held, pressed ? 1 : 0); }
    /** Count every press, including short taps completed between render frames. */
    public void setGuard(boolean held, int presses) {
        if (defeated()) { clearGuard(); return; }
        presses = Math.max(0, presses);
        if (staggered()) {
            guarding = held; guardAge = Double.POSITIVE_INFINITY; return;
        }
        if (!held) {
            finishGuardAttempt(); failTaps(presses);
            guarding = false; guardAge = 0; return;
        }
        if (!guarding || presses > 0) {
            finishGuardAttempt();
            failTaps(Math.max(0, presses - 1));
            guardWindow = Math.max(config.minimumDeflectWindow, config.deflectWindow - failedTaps * config.spamStep);
            guardAge = 0; guardSucceeded = false;
        }
        guarding = true;
    }
    public void clearGuard() { guarding = false; guardAge = 0; }
    public void updateDefense(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return;
        tapQuietTime += dt;
        if (tapQuietTime + 1e-9 >= config.spamRecovery) failedTaps = 0;
        if (guarding) guardAge += dt;
        posture.update(dt);
        feedbackTime = Math.max(0, feedbackTime - dt);
        if (feedbackTime == 0) impact = Impact.NONE;
    }
    /** Called once by an overlapping active enemy swing, never at windup/activation alone. */
    public void receiveAttack(Enemy attacker) {
        if (defeated()) return;
        posture.pressure();
        attacker.posture.pressure();
        if (guarding() && guardAge <= guardWindow + 1e-7) {
            impact = Impact.DEFLECT;
            guardSucceeded = true; failedTaps = 0; tapQuietTime = 0; guardWindow = config.deflectWindow;
            attacker.deflected(config.deflectDamage);
        } else if (guarding()) {
            impact = Impact.BLOCK;
            posture.add(config.blockDamage);
            if (staggered()) {
                impact = Impact.POSTURE_BREAK; attackTime = 0; guardAge = Double.POSITIVE_INFINITY;
            }
        } else {
            impact = Impact.HIT;
            takeDamage(Enemy.DAMAGE);
        }
        feedbackTime = .25;
    }
    public boolean defeated() { return health <= 0; }
    public void takeDamage(int amount) {
        health = (int) Math.max(0, (long) health - Math.max(0, amount));
        if (defeated()) { attackTime = 0; velocityY = 0; clearGuard(); }
    }
    public void reset() {
        x = 180; y = GameWorld.GROUND_Y - HEIGHT; velocityY = 0; health = maxHealth;
        facingRight = grounded = true; attackTime = cooldown = 0; hitThisAttack = false;
        clearGuard(); posture.reset(); impact = Impact.NONE; feedbackTime = 0;
        failedTaps = 0; tapQuietTime = 0; guardWindow = config.deflectWindow; guardSucceeded = false;
    }
    public void update(float dt, int move, boolean jump, boolean attack) {
        if (defeated()) return;
        attackTime = Math.max(0, attackTime - dt);
        cooldown = Math.max(0, cooldown - dt);
        if (staggered()) { move = 0; jump = attack = false; attackTime = 0; }
        if (move != 0) facingRight = move > 0;
        x += move * SPEED * dt;
        if (jump && grounded) { velocityY = -590; grounded = false; }
        velocityY += GameWorld.GRAVITY * dt;
        y += velocityY * dt;
        CollisionManager.resolveWorld(this);
        if (attack && cooldown == 0) {
            attackTime = ATTACK_DURATION;
            cooldown = COOLDOWN;
            hitThisAttack = false;
        }
    }

    public boolean canDeathblow(Enemy enemy) {
        if (defeated() || staggered() || enemy.defeated() || !enemy.posture.broken()) return false;
        float dx = enemy.x + Enemy.WIDTH / 2 - (x + WIDTH / 2);
        float gap = Math.abs(dx) - (WIDTH + Enemy.WIDTH) / 2;
        return (facingRight ? dx > 0 : dx < 0) && gap <= config.deathblowRange
                && y < enemy.y + Enemy.HEIGHT && y + HEIGHT > enemy.y;
    }
    public boolean tryDeathblow(Enemy enemy) {
        if (!canDeathblow(enemy)) return false;
        enemy.takeDamage(enemy.health);
        attackTime = 0; cooldown = COOLDOWN; hitThisAttack = true;
        impact = Impact.DEATHBLOW; feedbackTime = .6;
        return true;
    }
    public boolean attacking() { return attackTime > 0 && !staggered(); }
    public float attackLeft() { return facingRight ? x + WIDTH : x - ATTACK_RANGE; }
    public float attackRight() { return attackLeft() + ATTACK_RANGE; }
    public void tryHit(Enemy enemy) {
        if (!defeated() && attacking() && !hitThisAttack && !enemy.defeated()
                && CollisionManager.overlaps(attackLeft(), y + 10, attackRight(), y + HEIGHT - 8,
                    enemy.x, enemy.y, enemy.x + Enemy.WIDTH, enemy.y + Enemy.HEIGHT)) {
            posture.pressure();
            enemy.takeDamage(25);
            hitThisAttack = true;
        }
    }
}
