package com.lockin.game;

import java.util.Locale;

/** Windowed monotonic-loop measurements. No per-frame strings or allocation. */
public final class FrameStats {
  private double elapsed, updateSeconds, drawSeconds, advanced, dropped, worst;
  private long frames, steps;

  public void record(
      double dt, long updateNanos, long drawNanos, long ticks, double simulated, double discarded) {
    if (!Double.isFinite(dt) || dt <= 0) return;
    elapsed += dt;
    frames++;
    steps += Math.max(0, ticks);
    updateSeconds += Math.max(0, updateNanos) / 1e9;
    drawSeconds += Math.max(0, drawNanos) / 1e9;
    advanced += Math.max(0, simulated);
    dropped += Math.max(0, discarded);
    worst = Math.max(worst, dt);
  }

  public boolean ready() {
    return elapsed >= 5;
  }

  public double fps() {
    return elapsed > 0 ? frames / elapsed : 0;
  }

  public double updatesPerSecond() {
    return elapsed > 0 ? steps / elapsed : 0;
  }

  public double simulationRatio() {
    return elapsed > 0 ? advanced / elapsed : 0;
  }

  public double meanFrameMs() {
    return frames > 0 ? elapsed * 1000 / frames : 0;
  }

  public String summary(double backlog) {
    return String.format(
        Locale.ROOT,
        "fps=%.1f frame=%.1fms worst=%.1fms ticks/s=%.1f sim/real=%.3f update=%.2fms"
            + " draw+surface=%.2fms backlog=%.3fs discarded=%.3fs",
        fps(),
        meanFrameMs(),
        worst * 1000,
        updatesPerSecond(),
        simulationRatio(),
        frames > 0 ? updateSeconds * 1000 / frames : 0,
        frames > 0 ? drawSeconds * 1000 / frames : 0,
        backlog,
        dropped);
  }

  public void reset() {
    elapsed = updateSeconds = drawSeconds = advanced = dropped = worst = 0;
    frames = steps = 0;
  }
}
