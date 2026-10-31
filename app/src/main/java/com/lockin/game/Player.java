package com.lockin.game;

public final class Player {
    public static final float WIDTH = 36, HEIGHT = 64, SPEED = 250;
    public static final float ATTACK_RANGE = 68, ATTACK_DURATION = .16f, COOLDOWN = .45f;
    public float x = 180, y = GameWorld.GROUND_Y - HEIGHT;
    public float velocityY;
    public int health = 100;
    public boolean facingRight = true, grounded = true;
    public enum Impact { NONE, DEFLECT, BLOCK, HIT }
    public final Posture posture;
    private final CombatConfig config;
    private boolean guarding;
    private double guardAge, feedbackTime;
    private Impact impact = Impact.NONE;
    private float attackTime, cooldown;
    private boolean hitThisAttack;

    public Player() { this(CombatConfig.DEFAULT); }
    public Player(CombatConfig config) { this.config = config; posture = new Posture(config); }
    public boolean guarding() { return guarding; }
    public Impact impact() { return impact; }
    /** pressed preserves a release/repress edge even when both occur between frames. */
    public void setGuard(boolean held, boolean pressed) {
        if (!held || defeated()) { guarding = false; guardAge = 0; return; }
        if (!guarding || pressed) guardAge = 0;
        guarding = true;
    }
    public void clearGuard() { guarding = false; guardAge = 0; }
    public void updateDefense(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return;
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
        if (guarding && guardAge <= config.deflectWindow + 1e-7) {
            impact = Impact.DEFLECT;
            attacker.posture.add(config.deflectDamage);
        } else if (guarding) {
            impact = Impact.BLOCK;
            posture.add(config.blockDamage);
        } else {
            impact = Impact.HIT;
            takeDamage(Enemy.DAMAGE);
        }
        feedbackTime = .25;
    }
    public boolean defeated() { return health == 0; }
    public void takeDamage(int amount) {
        health = Math.max(0, health - Math.max(0, amount));
        if (defeated()) { attackTime = 0; velocityY = 0; clearGuard(); }
    }
    public void reset() {
        x = 180; y = GameWorld.GROUND_Y - HEIGHT; velocityY = 0; health = 100;
        facingRight = grounded = true; attackTime = cooldown = 0; hitThisAttack = false;
        clearGuard(); posture.reset(); impact = Impact.NONE; feedbackTime = 0;
    }
    public void update(float dt, int move, boolean jump, boolean attack) {
        if (defeated()) return;
        attackTime = Math.max(0, attackTime - dt);
        cooldown = Math.max(0, cooldown - dt);
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

    public boolean attacking() { return attackTime > 0; }
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
