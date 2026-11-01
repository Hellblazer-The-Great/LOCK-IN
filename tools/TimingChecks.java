package com.lockin.game;

/** Wall time is supplied deterministically to the same update path used by the Android loop. */
public final class TimingChecks {
  private static int checks;

  private static void check(boolean yes, String label) {
    checks++;
    if (!yes) throw new AssertionError(label);
  }

  private static void near(double a, double b, double epsilon, String label) {
    check(Math.abs(a - b) <= epsilon, label + " (" + a + ", " + b + ")");
  }

  private static Game practice() {
    Game g = Game.combatArena(CombatConfig.DEFAULT, 1000000, 1000000);
    g.enemy.detectionRadius = 0;
    return g;
  }

  private static void advance(Game g, int fps, float seconds) {
    for (int i = 0; i < Math.round(fps * seconds); i++) g.update(1f / fps, 0, false, false);
  }

  public static void main(String[] args) {
    for (int fps : new int[] {60, 30, 20, 15, 10, 5}) {
      Game g = practice();
      g.enemy.takeDamage(g.enemy.health);
      for (int i = 0; i < fps; i++) g.update(1f / fps, 1, false, false);
      near(g.simulationSeconds(), 1, 1e-6, "simulation keeps wall time at " + fps + " FPS");
      check(g.simulationSteps() == 120, "120 ticks per wall second at " + fps);
      near(g.player.x, 180+Player.SPEED, .02, "normal movement distance at " + fps);
      check(g.droppedSeconds() == 0, "ordinary slow rendering discards no time at " + fps);
      g = practice();
      g.player.startDash(1);
      advance(g, fps, 1);
      near(g.player.x, 351, .02, "dash burst distance at " + fps);
      check(
          !g.player.dashing() && !g.player.invulnerable() && g.player.dashCooldownRemaining() == 0,
          "dash/i-frames/cooldown expire on wall time at " + fps);
      g = practice();
      g.update(1f / fps, 0, true, false);
      advance(g, fps, 1);
      check(
          g.player.grounded && g.player.velocityY == 0, "jump lands within real second at " + fps);
      Game ref = practice();
      g = practice();
      g.update(1f / fps, 0, true, false);
      for (int i = 1; i < fps; i++) g.update(1f / fps, 0, false, false);
      ref.update(1f / 120, 0, true, false);
      for (int i = 1; i < 120; i++) ref.update(1f / 120, 0, false, false);
      near(
          g.player.y, ref.player.y, .001, "jump trajectory agrees with 120 Hz reference at " + fps);
      g = practice();
      g.player.x = g.enemy.x - Player.WIDTH - 20;
      g.enemy.detectionRadius = 340;
      ref = practice();
      ref.player.x = ref.enemy.x - Player.WIDTH - 20;
      ref.enemy.detectionRadius = 340;
      for (int i = 0; i < fps * 3; i++) g.update(1f / fps, 0, false, false);
      for (int i = 0; i < 360; i++) ref.update(1f / 120, 0, false, false);
      check(
          g.enemy.move() == ref.enemy.move()
              && g.enemy.state() == ref.enemy.state()
              && g.enemy.strikeNumber() == ref.enemy.strikeNumber(),
          "enemy phases/cooldowns match wall time at " + fps);
      near(g.player.health, ref.player.health, 0, "damage timing at " + fps);
      near(
          g.enemy.phaseProgress(),
          ref.enemy.phaseProgress(),
          .00001,
          "windup/active/recovery progress at " + fps);
      g = practice();
      CharacterAnimations animations = new CharacterAnimations();
      animations.update(g, 0);
      for (int i = 0; i < fps; i++) {
        g.update(1f / fps, 0, false, false);
        animations.update(g, g.lastAdvanceSeconds());
      }
      check(
          animations.player.frame() == 4, "12 fps idle animation advances a real second at " + fps);
      near(g.simulationSeconds(), 1, 1e-6, "presentation does not advance simulation");
      FrameStats stats = new FrameStats();
      for (int i = 0; i < fps * 5; i++)
        stats.record(1.0 / fps, 100000, 200000, 120 / fps, 1.0 / fps, 0);
      near(stats.fps(), fps, .000001, "recorded measured FPS");
      near(stats.updatesPerSecond(), 120, .000001, "measured fixed-step throughput");
      near(stats.simulationRatio(), 1, .000001, "recorded simulation/wall ratio");
      near(stats.meanFrameMs(), 1000.0 / fps, .000001, "recorded frame milliseconds");
    }
    Game g = practice();
    g.update(.4f, 0, false, false);
    check(
        g.simulationSteps() == Game.MAX_STEPS_PER_UPDATE,
        "catch-up work is bounded per render iteration");
    check(
        g.backlogSeconds() > .1 && g.droppedSeconds() == 0, "excess work retained for next frame");
    g.update(1f / 60, 0, false, false);
    near(
        g.simulationSeconds() + g.backlogSeconds(),
        .4 + 1.0 / 60,
        .00001,
        "retained backlog never silently discarded");
    g = practice();
    g.update(5, 0, false, false);
    check(g.droppedSeconds() >= 4.5, "pathological stalls explicitly counted");
    check(g.backlogSeconds() < Game.MAX_BACKLOG_SECONDS, "pathological backlog bounded");
    for (int i = 0; i < 3; i++) g.update(1f / 60, 0, false, false);
    check(g.backlogSeconds() < Game.STEP, "stalled backlog drains");
    g.requestReset();
    double time = g.simulationSeconds();
    g.update(.2f, 1, true, true);
    near(g.simulationSeconds(), time, 0, "reset modal freezes simulation");
    g.cancelReset();
    g.update(1f / 60, 0, false, false);
    near(g.lastAdvanceSeconds(), 1.0 / 60, 1e-6, "cancel does not replay paused time");
    g.requestReset();
    g.confirmReset();
    check(
        g.simulationSteps() == 0 && g.droppedSeconds() == 0 && g.backlogSeconds() == 0,
        "restart clears all clock state");
    g.update(Float.NaN, 1, false, false);
    g.update(Float.POSITIVE_INFINITY, 1, false, false);
    g.update(-1, 1, false, false);
    check(g.simulationSteps() == 0, "invalid clock intervals ignored");
    for (SpriteClip clip : SpriteClip.values()) {
      boolean ninja = clip.name().startsWith("NINJA");
      near(clip.scale, ninja ? 3.4 : 8.2, .00001, "uniform 1.5x render size " + clip);
      near((-clip.anchorY + clip.anchorY) * clip.scale, 0, 0, "feet anchor stays fixed " + clip);
      check(clip.flip(true) != clip.flip(false), "facing preserved " + clip);
    }
    check(
        Player.WIDTH == 36 && Player.HEIGHT == 64 && Enemy.WIDTH == 44 && Enemy.HEIGHT == 70,
        "physics bodies unchanged");
    check(
        Player.SPEED == 180 && Enemy.SPEED == 110 && Player.ATTACK_RANGE == 68,
        "balance constants unchanged");
    System.out.println("Passed " + checks + " real-time/scale/telemetry checks");
  }
}
