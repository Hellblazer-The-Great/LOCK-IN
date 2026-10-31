package com.lockin.game;

public final class Player {
    public static final float WIDTH = 36, HEIGHT = 64, SPEED = 250;
    public static final float ATTACK_RANGE = 68, ATTACK_DURATION = .16f, COOLDOWN = .45f;
    public static final int DAMAGE = 25;
    public static final double POSTURE_DAMAGE = 25;
    public static final double DASH_DURATION = .180, DASH_IFRAMES = .100, DASH_COOLDOWN = .450;
    public static final double DASH_IFRAME_START = (DASH_DURATION - DASH_IFRAMES) / 2;
    public static final double DASH_IFRAME_END = DASH_IFRAME_START + DASH_IFRAMES;
    public static final float DASH_SPEED = 950;
    public static final double DEATHBLOW_DURATION = .30;
    private double dashAge = DASH_DURATION, dashCooldown, deathblowTime;
    private int dashDirection;
    private boolean guardNeedsFreshPress;
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
    public boolean guarding() { return guarding && !staggered() && !defeated() && !dashing() && !deathblowing(); }
    public boolean dashing() { return dashAge < DASH_DURATION - 1e-9; }
    public boolean invulnerable() {
        return dashing() && dashAge + 1e-9 >= DASH_IFRAME_START && dashAge < DASH_IFRAME_END - 1e-9;
    }
    public boolean deathblowing() { return deathblowTime > 1e-7; }
    public int dashDirection() { return dashDirection; }
    public double dashCooldownRemaining() { return dashCooldown; }
    public boolean startDash(int move) {
        if (defeated() || dashing() || deathblowing() || dashCooldown > 1e-7) return false;
        dashDirection = move == 0 ? (facingRight ? -1 : 1) : (move > 0 ? 1 : -1);
        if (move != 0) facingRight = move > 0;
        dashAge = 0; dashCooldown = DASH_COOLDOWN;
        attackTime = 0; hitThisAttack = true;
        clearGuard(); guardNeedsFreshPress = true;
        posture.cancelBreak();
        return true;
    }
    /** Read-only presentation progress; rendering never drives combat timers. */
    public float attackProgress() { return Vitals.fraction(ATTACK_DURATION - attackTime, ATTACK_DURATION); }
    public boolean attackRecovering() { return !attacking() && cooldown > 0 && !dashing() && !deathblowing(); }
    public float deathblowProgress() { return Vitals.fraction(DEATHBLOW_DURATION - deathblowTime, DEATHBLOW_DURATION); }
    public float impactProgress() {
        double duration = impact == Impact.DEATHBLOW ? .6 : .25;
        return Vitals.fraction(duration - feedbackTime, duration);
    }
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
        if (dashing() || deathblowing()) { clearGuard(); guardNeedsFreshPress = true; return; }
        presses = Math.max(0, presses);
        if (guardNeedsFreshPress) {
            if (held && presses == 0) { guarding = true; guardAge = Double.POSITIVE_INFINITY; return; }
            guardNeedsFreshPress = false;
        }
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
        if (defeated() || attacker.defeated() || invulnerable()) return;
        posture.pressure();
        attacker.posture.pressure();
        if (guarding() && guardAge <= guardWindow + 1e-7) {
            impact = Impact.DEFLECT;
            guardSucceeded = true; failedTaps = 0; tapQuietTime = 0; guardWindow = config.deflectWindow;
            attacker.deflected(config.deflectDamage * attacker.damageMultiplier());
        } else if (guarding()) {
            impact = Impact.BLOCK;
            posture.add(config.blockDamage * attacker.damageMultiplier());
            if (staggered()) {
                impact = Impact.POSTURE_BREAK; attackTime = 0; guardAge = Double.POSITIVE_INFINITY;
            }
        } else {
            impact = Impact.HIT;
            posture.add(attacker.postureDamage());
            takeDamage(attacker.healthDamage());
            if (staggered()) { attackTime = 0; guardAge = Double.POSITIVE_INFINITY; }
        }
        feedbackTime = .25;
    }
    public boolean defeated() { return health <= 0; }
    public void takeDamage(int amount) {
        health = (int) Math.max(0, (long) health - Math.max(0, amount));
        if (defeated()) { attackTime = 0; velocityY = 0; clearGuard(); dashAge = DASH_DURATION; deathblowTime = 0; }
    }
    public void reset() {
        x = 180; y = GameWorld.GROUND_Y - HEIGHT; velocityY = 0; health = maxHealth;
        facingRight = grounded = true; attackTime = cooldown = 0; hitThisAttack = false;
        clearGuard(); posture.reset(); impact = Impact.NONE; feedbackTime = 0;
        dashAge = DASH_DURATION; dashCooldown = deathblowTime = 0; dashDirection = 0; guardNeedsFreshPress = false;
        failedTaps = 0; tapQuietTime = 0; guardWindow = config.deflectWindow; guardSucceeded = false;
    }
    public void update(float dt, int move, boolean jump, boolean attack) {
        if (defeated() || !Float.isFinite(dt) || dt <= 0) return;
        boolean movingDash = dashing(), executing = deathblowing();
        dashCooldown = Math.max(0, dashCooldown - dt);
        deathblowTime = Math.max(0, deathblowTime - dt);
        attackTime = Math.max(0, attackTime - dt);
        cooldown = Math.max(0, cooldown - dt);
        if (staggered() || executing) { move = 0; jump = attack = false; attackTime = 0; }
        if (!movingDash && move != 0) facingRight = move > 0;
        if (movingDash) {
            double burst = Math.min(dt, DASH_DURATION - dashAge);
            x += dashDirection * DASH_SPEED * burst;
            dashAge = Math.min(DASH_DURATION, dashAge + dt);
            x += move * SPEED * Math.max(0, dt - burst);
            attack = false;
        } else x += move * SPEED * dt;
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
        if (defeated() || staggered() || dashing() || deathblowing() || enemy.defeated() || !enemy.posture.broken()) return false;
        float dx = enemy.x + Enemy.WIDTH / 2 - (x + WIDTH / 2);
        float gap = Math.abs(dx) - (WIDTH + Enemy.WIDTH) / 2;
        return (facingRight ? dx > 0 : dx < 0) && gap <= config.deathblowRange
                && y < enemy.y + Enemy.HEIGHT && y + HEIGHT > enemy.y;
    }
    public boolean tryDeathblow(Enemy enemy) {
        if (!canDeathblow(enemy)) return false;
        enemy.takeDamage(enemy.health);
        deathblowTime = DEATHBLOW_DURATION;
        clearGuard(); guardNeedsFreshPress = true;
        attackTime = 0; cooldown = COOLDOWN; hitThisAttack = true;
        impact = Impact.DEATHBLOW; feedbackTime = .6;
        return true;
    }
    public boolean attacking() { return attackTime > 0 && !staggered() && !dashing() && !deathblowing(); }
    public float attackLeft() { return facingRight ? x + WIDTH : x - ATTACK_RANGE; }
    public float attackRight() { return attackLeft() + ATTACK_RANGE; }
    public void tryHit(Enemy enemy) {
        if (!defeated() && attacking() && !hitThisAttack && !enemy.defeated()
                && CollisionManager.overlaps(attackLeft(), y + 10, attackRight(), y + HEIGHT - 8,
                    enemy.x, enemy.y, enemy.x + Enemy.WIDTH, enemy.y + Enemy.HEIGHT)) {
            posture.pressure();
            enemy.receiveHit(DAMAGE, POSTURE_DAMAGE);
            hitThisAttack = true;
        }
    }
}
