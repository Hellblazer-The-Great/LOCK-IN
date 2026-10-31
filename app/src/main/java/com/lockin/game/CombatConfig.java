package com.lockin.game;

/** Immutable, shared tuning in seconds and posture units. No Android dependencies. */
public final class CombatConfig {
    public static final CombatConfig DEFAULT = new CombatConfig(.250, 100, 12, 25, 35, 1, 1);
    public final double deflectWindow, postureCapacity, postureRecovery;
    public final double blockDamage, deflectDamage, recoveryDelay, brokenDuration;

    public CombatConfig(double deflectWindow, double postureCapacity, double postureRecovery,
                        double blockDamage, double deflectDamage, double recoveryDelay, double brokenDuration) {
        double[] values = {deflectWindow, postureCapacity, postureRecovery, blockDamage,
                deflectDamage, recoveryDelay, brokenDuration};
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid combat tuning");
        }
        if (postureCapacity == 0 || brokenDuration == 0) throw new IllegalArgumentException("Zero capacity/duration");
        this.deflectWindow = deflectWindow;
        this.postureCapacity = postureCapacity;
        this.postureRecovery = postureRecovery;
        this.blockDamage = blockDamage;
        this.deflectDamage = deflectDamage;
        this.recoveryDelay = recoveryDelay;
        this.brokenDuration = brokenDuration;
    }
}
