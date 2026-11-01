package com.lockin.game;

/** Desktop probe: supplied elapsed time models render cost; no device FPS claims. */
public final class TimingProbe {
  public static void main(String[] args) {
    for (int fps : new int[] {60, 30, 20, 15, 10, 5}) {
      Game g = Game.combatArena();
      g.enemy.takeDamage(100);
      double travel = 0;
      Game idle = Game.combatArena();
      CharacterAnimations a = new CharacterAnimations();
      a.update(idle, 0);
      double wall = 0;
      for (int i = 0; i < fps * 10; i++) {
        g.player.x = 180;
        g.update(1f / fps, 1, false, false);
        travel += g.player.x - 180;
        a.update(idle, 1.0 / fps);
        wall += 1.0 / fps;
      }
      System.out.printf(
          "%2d FPS: elapsed=%.3fs movement-derived simulation=%.3fs ratio=%.3f ticks/s=%.1f"
              + " animation frame=%d%n",
          fps,
          wall,
          travel / Player.SPEED,
          travel / Player.SPEED / wall,
          travel / Player.SPEED / wall * 120,
          a.player.frame());
    }
    Game g = new Game(CombatConfig.DEFAULT, 1000000, 1000000);
    g.player.x = 2200;
    g.player.y = WorldDefinition.GROUND-Player.HEIGHT;
    g.player.health = 1000000;
    for (Enemy e : g.enemies()) e.detectionRadius = 4000;
    for (int i = 0; i < 10000; i++) g.update(1f / 120, 0, false, false);
    long start = System.nanoTime();
    for (int i = 0; i < 120000; i++) {
      g.player.health = 1000000;
      g.update(1f / 120, 0, false, false);
    }
    double seconds = (System.nanoTime() - start) / 1e9;
    System.out.printf(
        "Host five-enemy continuous-world simulation: %.3fs / 120000 ticks, %.1f us/tick%n",
        seconds, seconds * 1e6 / 120000);
  }
}
