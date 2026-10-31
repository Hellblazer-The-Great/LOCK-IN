package com.lockin.game;

/** Shared safe fractions for fixed-width HUD bars, independent of Android rendering. */
public final class Vitals {
    private Vitals() {}
    public static float fraction(double current, double maximum) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0) return 0;
        return (float) Math.max(0, Math.min(1, current / maximum));
    }
}
