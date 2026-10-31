package com.lockin.game;

/** Fixed moveset and balancing data: seconds and world pixels, no per-frame allocations. */
public enum EnemyMove {
    NORMAL_SLASH(1, 1, Enemy.WINDUP, Enemy.ACTIVE, Enemy.RECOVERY, 0, Enemy.ATTACK_RANGE, 10, 52),
    DOUBLE_SLASH(2, 1, .40f, .14f, .65f, .28f, 54, 10, 52),
    HEAVY_OVERHEAD(1, 2, .85f, .22f, 1.0f, 0, 48, -32, 102);

    public static final float BETWEEN_MOVES = .12f;
    public final int strikes, damageMultiplier;
    public final float windup, active, recovery, betweenStrikes, reach, topOffset, hitboxHeight;
    EnemyMove(int strikes, int multiplier, float windup, float active, float recovery,
              float betweenStrikes, float reach, float topOffset, float hitboxHeight) {
        this.strikes = strikes; damageMultiplier = multiplier;
        this.windup = windup; this.active = active; this.recovery = recovery;
        this.betweenStrikes = betweenStrikes; this.reach = reach;
        this.topOffset = topOffset; this.hitboxHeight = hitboxHeight;
    }
}
