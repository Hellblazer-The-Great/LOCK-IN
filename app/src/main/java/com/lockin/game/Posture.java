package com.lockin.game;

/** A full meter signals a temporary break only; no stagger/deathblow in Phase 2B. */
public final class Posture {
    private final CombatConfig config;
    private double value, quietTime, brokenTime;

    public Posture(CombatConfig config) { this.config = config; }
    public double value() { return value; }
    public float fraction() { return (float) (value / config.postureCapacity); }
    public boolean broken() { return brokenTime > 0; }
    public void pressure() { quietTime = 0; }
    public void add(double amount) {
        pressure();
        if (!Double.isFinite(amount) || amount <= 0 || broken()) return;
        value = Math.min(config.postureCapacity, value + amount);
        if (value >= config.postureCapacity) brokenTime = config.brokenDuration;
    }
    public void update(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return;
        if (broken()) {
            brokenTime = Math.max(0, brokenTime - dt);
            if (brokenTime <= 1e-9) { reset(); }
            return;
        }
        // Integrate only the portion after the quiet delay, including partial steps.
        double before = Math.max(0, quietTime - config.recoveryDelay);
        quietTime += dt;
        double recoveryTime = Math.max(0, quietTime - config.recoveryDelay) - before;
        value = Math.max(0, value - config.postureRecovery * recoveryTime);
    }
    public void reset() { value = quietTime = brokenTime = 0; }
}
