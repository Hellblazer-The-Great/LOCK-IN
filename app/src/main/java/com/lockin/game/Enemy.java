package com.lockin.game;

/** Deterministic melee opponent. Facing is committed from windup through recovery. */
public final class Enemy {
    public enum State { IDLE, CHASE, ATTACK_WINDUP, ATTACK_ACTIVE, ATTACK_RECOVERY, POSTURE_BROKEN, DEAD }
    public static final float WIDTH = 44, HEIGHT = 70, SPEED = 110;
    public static final float DEFAULT_DETECTION_RADIUS = 340, MELEE_DISTANCE = 30;
    public static final float ATTACK_RANGE = 64, WINDUP = .5f, ACTIVE = .18f, RECOVERY = .7f;
    public static final int DAMAGE = 20;
    public float x = 670, y = GameWorld.GROUND_Y - HEIGHT;
    /** Center-to-center distance in world pixels; zero disables detection for practice/tests. */
    public float detectionRadius = DEFAULT_DETECTION_RADIUS;
    public static final int DEFAULT_MAX_HEALTH = 100;
    public final int maxHealth;
    public int health;
    public final Posture posture;
    public boolean facingRight;
    private State state = State.IDLE;
    private float stateTime;
    private boolean hitThisAttack;

    public Enemy() { this(CombatConfig.DEFAULT); }
    public Enemy(CombatConfig config) { this(config, DEFAULT_MAX_HEALTH); }
    public Enemy(CombatConfig config, int maxHealth) {
        this.maxHealth = Math.max(0, maxHealth); posture = new Posture(config); reset();
    }
    public float healthFraction() { return Vitals.fraction(health, maxHealth); }
    public void deflected(double amount) {
        posture.add(amount);
        if (posture.broken()) { enter(State.POSTURE_BROKEN, 0); hitThisAttack = true; }
    }
    public State state() { return defeated() ? State.DEAD : posture.broken() ? State.POSTURE_BROKEN : state; }
    public void reset() {
        posture.reset();
        x = 670; y = GameWorld.GROUND_Y - HEIGHT; health = maxHealth;
        facingRight = false; state = State.IDLE; stateTime = 0; hitThisAttack = false;
    }
    public void takeDamage(int amount) {
        if (amount > 0) posture.pressure();
        health = (int) Math.max(0, (long) health - Math.max(0, amount));
        if (defeated()) { state = State.DEAD; stateTime = 0; }
    }
    public boolean defeated() { return health <= 0; }
    public boolean detects(Player player) {
        float dx = player.x + Player.WIDTH / 2 - (x + WIDTH / 2);
        float dy = player.y + Player.HEIGHT / 2 - (y + HEIGHT / 2);
        return detectionRadius > 0 && dx * dx + dy * dy <= detectionRadius * detectionRadius;
    }
    private void enter(State next, float duration) { state = next; stateTime = duration; }
    public void update(float dt, Player player) {
        if (!Float.isFinite(dt) || dt <= 0 || player.defeated()) return;
        if (defeated()) { state = State.DEAD; return; }
        if (posture.broken()) { enter(State.POSTURE_BROKEN, 0); hitThisAttack = true; return; }
        if (state == State.POSTURE_BROKEN) { enter(State.ATTACK_RECOVERY, RECOVERY); return; }
        if (state == State.ATTACK_WINDUP || state == State.ATTACK_ACTIVE
                || state == State.ATTACK_RECOVERY) {
            stateTime = Math.max(0, stateTime - dt);
            if (stateTime == 0) {
                switch (state) {
                    case ATTACK_WINDUP: enter(State.ATTACK_ACTIVE, ACTIVE); break;
                    case ATTACK_ACTIVE: enter(State.ATTACK_RECOVERY, RECOVERY); break;
                    default: enter(State.IDLE, 0); break;
                }
            }
            return;
        }
        if (!detects(player)) { state = State.IDLE; return; }
        float dx = player.x + Player.WIDTH / 2 - (x + WIDTH / 2);
        if (dx != 0) facingRight = dx > 0;
        float gap = Math.abs(dx) - (WIDTH + Player.WIDTH) / 2;
        boolean sameHeight = player.y < y + HEIGHT - 8 && player.y + Player.HEIGHT > y + 10;
        if (gap <= MELEE_DISTANCE + .001f && sameHeight) {
            hitThisAttack = false;
            enter(State.ATTACK_WINDUP, WINDUP);
        } else {
            state = State.CHASE;
            float distance = Math.min(SPEED * dt, Math.max(0, gap - MELEE_DISTANCE));
            x += (facingRight ? 1 : -1) * distance;
            x = Math.max(0, Math.min(GameWorld.WIDTH - WIDTH, x));
        }
    }
    public float attackLeft() { return facingRight ? x + WIDTH : x - ATTACK_RANGE; }
    public float attackRight() { return attackLeft() + ATTACK_RANGE; }
    public void tryHit(Player player) {
        if (state() == State.ATTACK_ACTIVE && !defeated() && !player.defeated() && !hitThisAttack
                && CollisionManager.overlaps(attackLeft(), y + 10, attackRight(), y + HEIGHT - 8,
                    player.x, player.y, player.x + Player.WIDTH, player.y + Player.HEIGHT)) {
            player.receiveAttack(this);
            hitThisAttack = true;
        }
    }
}
