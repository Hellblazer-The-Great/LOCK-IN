package com.lockin.game;

/** Timed break followed by immunity; new pressure cannot extend either timer. */
public final class Posture {
    private final CombatConfig config;
    private double value, quietTime, brokenTime, immunityTime;

    public Posture(CombatConfig config) { this.config = config; }
    public double value() { return value; }
    public float fraction() { return Vitals.fraction(value, config.postureCapacity); }
    public boolean broken() { return brokenTime > 0; }
    public boolean immune() { return immunityTime > 0; }
    public float breakFraction() { return Vitals.fraction(brokenTime, config.brokenDuration); }
    public void pressure() { quietTime = 0; }
    public void add(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        pressure();
        if (broken() || immune()) return;
        value = Math.min(config.postureCapacity, value + amount);
        if (value >= config.postureCapacity) brokenTime = config.brokenDuration;
    }
    public void update(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return;
        if (broken()) {
            double oldBrokenTime = brokenTime;
            brokenTime = Math.max(0, brokenTime - dt);
            if (brokenTime <= 1e-9) {
                double leftover = Math.max(0, dt - oldBrokenTime);
                value = quietTime = brokenTime = 0;
                immunityTime = Math.max(0, config.breakImmunity - leftover);
            }
            return;
        }
        immunityTime = Math.max(0, immunityTime - dt);
        // Integrate only the portion after the quiet delay, including partial steps.
        double before = Math.max(0, quietTime - config.recoveryDelay);
        quietTime += dt;
        double recoveryTime = Math.max(0, quietTime - config.recoveryDelay) - before;
        value = Math.max(0, value - config.postureRecovery * recoveryTime);
    }
    public void reset() { value = quietTime = brokenTime = immunityTime = 0; }
}
