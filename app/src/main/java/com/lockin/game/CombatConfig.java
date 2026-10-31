package com.lockin.game;

/** Immutable, shared tuning in seconds and posture units. No Android dependencies. */
public final class CombatConfig {
    public static final CombatConfig DEFAULT = new CombatConfig(.250, 100, 12, 25, 35, 1, 1);
    public final double deflectWindow, postureCapacity, postureRecovery;
    public final double blockDamage, deflectDamage, recoveryDelay, brokenDuration;

    public final double spamStep, minimumDeflectWindow, spamRecovery, breakImmunity, deathblowRange;

    public CombatConfig(double deflectWindow, double postureCapacity, double postureRecovery,
                        double blockDamage, double deflectDamage, double recoveryDelay, double brokenDuration) {
        this(deflectWindow, postureCapacity, postureRecovery, blockDamage, deflectDamage,
                recoveryDelay, brokenDuration, .05, Math.min(.05, deflectWindow), .8, 1, 90);
    }
    public CombatConfig(double deflectWindow, double postureCapacity, double postureRecovery,
                        double blockDamage, double deflectDamage, double recoveryDelay, double brokenDuration,
                        double spamStep, double minimumDeflectWindow, double spamRecovery,
                        double breakImmunity, double deathblowRange) {
        double[] values = {deflectWindow, postureCapacity, postureRecovery, blockDamage,
                deflectDamage, recoveryDelay, brokenDuration, spamStep, minimumDeflectWindow,
                spamRecovery, breakImmunity, deathblowRange};
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid combat tuning");
        }
        if (postureCapacity == 0 || brokenDuration == 0) throw new IllegalArgumentException("Zero capacity/duration");
        if (minimumDeflectWindow > deflectWindow || spamRecovery == 0 || breakImmunity == 0)
            throw new IllegalArgumentException("Invalid guard/break tuning");
        this.spamStep = spamStep; this.minimumDeflectWindow = minimumDeflectWindow;
        this.spamRecovery = spamRecovery; this.breakImmunity = breakImmunity;
        this.deathblowRange = deathblowRange;
        this.deflectWindow = deflectWindow;
        this.postureCapacity = postureCapacity;
        this.postureRecovery = postureRecovery;
        this.blockDamage = blockDamage;
        this.deflectDamage = deflectDamage;
        this.recoveryDelay = recoveryDelay;
        this.brokenDuration = brokenDuration;
    }
}
