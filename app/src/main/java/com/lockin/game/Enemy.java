package com.lockin.game;

/** Deterministic melee opponent. Facing is committed from windup through recovery. */
public final class Enemy {
    public enum State { IDLE, CHASE, ATTACK_WINDUP, ATTACK_ACTIVE, ATTACK_RECOVERY, POSTURE_BROKEN, DEAD }
    public static final float WIDTH = 44, HEIGHT = 70, SPEED = 110;
    public static final float DEFAULT_DETECTION_RADIUS = 340, MELEE_DISTANCE = 30;
    public static final float ATTACK_RANGE = 64, WINDUP = .5f, ACTIVE = .18f, RECOVERY = .7f;
    public static final int DAMAGE = 20;
    public static final double POSTURE_DAMAGE = 25;
    private static final EnemyMove[] SEQUENCE = EnemyMove.values();
    private EnemyMove move = EnemyMove.NORMAL_SLASH;
    private int nextMove, strike;
    private boolean moveInProgress;
    private float betweenMoves;
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
    public EnemyMove move() { return move; }
    public EnemyMove nextMove() { return SEQUENCE[nextMove]; }
    public int strikeNumber() { return strike + 1; }
    public int damageMultiplier() { return move.damageMultiplier; }
    public int healthDamage() { return DAMAGE * damageMultiplier(); }
    public double postureDamage() { return POSTURE_DAMAGE * damageMultiplier(); }
    private void resolveMove() {
        if (moveInProgress) { nextMove = (nextMove + 1) % SEQUENCE.length; moveInProgress = false; }
    }
    private void breakPosture() {
        resolveMove(); enter(State.POSTURE_BROKEN, 0); hitThisAttack = true;
    }
    public void receiveHit(int healthDamage, double postureDamage) {
        if (defeated()) return;
        // Both components belong to this living contact, including a lethal health hit.
        posture.add(postureDamage);
        takeDamage(healthDamage);
        if (!defeated() && posture.broken()) breakPosture();
    }
    public float healthFraction() { return Vitals.fraction(health, maxHealth); }
    public void deflected(double amount) {
        if (defeated()) return;
        posture.add(amount);
        if (posture.broken()) breakPosture();
    }
    public State state() { return defeated() ? State.DEAD : posture.broken() ? State.POSTURE_BROKEN : state; }
    public void reset() {
        posture.reset();
        move = EnemyMove.NORMAL_SLASH; nextMove = strike = 0; moveInProgress = false; betweenMoves = 0;
        x = 670; y = GameWorld.GROUND_Y - HEIGHT; health = maxHealth;
        facingRight = false; state = State.IDLE; stateTime = 0; hitThisAttack = false;
    }
    public void takeDamage(int amount) {
        if (amount > 0) posture.pressure();
        health = (int) Math.max(0, (long) health - Math.max(0, amount));
        if (defeated()) {
            state = State.DEAD; stateTime = 0;
            nextMove = strike = 0; move = EnemyMove.NORMAL_SLASH; moveInProgress = false;
        }
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
        if (posture.broken()) { breakPosture(); return; }
        if (state == State.POSTURE_BROKEN) { enter(State.ATTACK_RECOVERY, RECOVERY); return; }
        if (state == State.ATTACK_WINDUP || state == State.ATTACK_ACTIVE
                || state == State.ATTACK_RECOVERY) {
            stateTime = Math.max(0, stateTime - dt);
            if (stateTime == 0) {
                switch (state) {
                    case ATTACK_WINDUP:
                        hitThisAttack = false;
                        enter(State.ATTACK_ACTIVE, move.active); break;
                    case ATTACK_ACTIVE:
                        if (strike + 1 < move.strikes) {
                            strike++;
                            enter(State.ATTACK_WINDUP, move.betweenStrikes);
                        } else enter(State.ATTACK_RECOVERY, move.recovery);
                        break;
                    default:
                        resolveMove(); betweenMoves = EnemyMove.BETWEEN_MOVES;
                        enter(State.IDLE, 0); break;
                }
            }
            return;
        }
        betweenMoves = Math.max(0, betweenMoves - dt);
        if (!detects(player)) { state = State.IDLE; return; }
        float dx = player.x + Player.WIDTH / 2 - (x + WIDTH / 2);
        if (dx != 0) facingRight = dx > 0;
        float gap = Math.abs(dx) - (WIDTH + Player.WIDTH) / 2;
        boolean sameHeight = player.y < y + HEIGHT - 8 && player.y + Player.HEIGHT > y + 10;
        if (gap <= MELEE_DISTANCE + .001f && sameHeight) {
            if (betweenMoves > 0) { state = State.IDLE; return; }
            move = SEQUENCE[nextMove]; strike = 0; moveInProgress = true;
            hitThisAttack = false;
            enter(State.ATTACK_WINDUP, move.windup);
        } else {
            state = State.CHASE;
            float distance = Math.min(SPEED * dt, Math.max(0, gap - MELEE_DISTANCE));
            x += (facingRight ? 1 : -1) * distance;
            x = Math.max(0, Math.min(GameWorld.WIDTH - WIDTH, x));
        }
    }
    public float attackLeft() { return facingRight ? x + WIDTH : x - move.reach; }
    public float attackRight() { return attackLeft() + move.reach; }
    public float attackTop() { return y + move.topOffset; }
    public float attackBottom() { return attackTop() + move.hitboxHeight; }
    public void tryHit(Player player) {
        if (state() == State.ATTACK_ACTIVE && !defeated() && !player.defeated() && !hitThisAttack
                && CollisionManager.overlaps(attackLeft(), attackTop(), attackRight(), attackBottom(),
                    player.x, player.y, player.x + Player.WIDTH, player.y + Player.HEIGHT)) {
            player.receiveAttack(this);
            hitThisAttack = true;
        }
    }
}
