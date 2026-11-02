package com.lockin.game;

import com.lockin.game.content.*;

/** Existing pose priorities; all state-to-clip and attack phase mappings come from content. */
public final class ContentAnimations {
  public final ContentAnimationClock player, enemy;
  private String playerPose = "idle", enemyPose = "idle";
  private long restart = Long.MIN_VALUE;
  private float playerX, enemyX;
  private int enemyHealth;
  private double hurt;

  public ContentAnimations(Content content) {
    player = new ContentAnimationClock(content);
    enemy = new ContentAnimationClock(content);
  }

  public String playerPose() {
    return playerPose;
  }

  public String enemyPose() {
    return enemyPose;
  }

  private void reset(Game g) {
    if (restart == g.restartVersion()) return;
    player.reset();
    enemy.reset();
    restart = g.restartVersion();
    playerX = g.player.x;
    enemyX = Float.NaN;
    enemyHealth = Integer.MAX_VALUE;
    hurt = 0;
  }

  public void updatePlayer(Game g, double dt) {
    reset(g);
    Player p = g.player;
    boolean sync = false;
    String state;
    if (p.defeated()) state = "death";
    else if (p.deathblowing()) state = "deathblow";
    else if (p.dashing()) state = "dash";
    else if (p.staggered()) state = "stagger";
    else if (p.attackRun.running() && p.attackRun.phase() != AttackRun.Phase.RECOVERY) {
      state = p.attackRun.phase().name().toLowerCase(java.util.Locale.ROOT);
      player.play(
          p.attackRun.animation(),
          100
              + p.attackRun.serial() * 100
              + p.attackRun.strike() * 4
              + p.attackRun.phase().ordinal());
      player.sample(p.attackRun.progress());
      sync = true;
    } else if (p.impact() == Player.Impact.DEFLECT) state = "deflect";
    else if (p.impact() == Player.Impact.HIT || p.impact() == Player.Impact.POSTURE_BREAK)
      state = "hurt";
    else if (p.guarding()) state = "guard";
    else if (p.climbing) state = "run";
    else if (!p.grounded) state = p.velocityY < 0 ? "jump" : "fall";
    else if (Math.abs(p.x - playerX) > .001) state = "run";
    else if (p.attackRun.running()) {
      state = "recovery";
      player.play(p.attackRun.animation(), 100 + p.attackRun.serial() * 100 + 3);
      player.sample(p.attackRun.progress());
      sync = true;
    } else state = "idle";
    if (!sync) {
      player.play(p.definition.animations.get(state), state.hashCode());
      if (p.deathblowing()) player.sample(p.deathblowProgress());
      else if ((state.equals("deflect") || state.equals("hurt"))
          && p.impact() != Player.Impact.NONE) player.sample(p.impactProgress());
      else player.advance(dt);
    }
    playerPose = state;
    playerX = p.x;
  }

  public void updateEnemy(Game g, double dt, Enemy e) {
    reset(g);
    hurt = Math.max(0, hurt - dt);
    if (enemyHealth != Integer.MAX_VALUE && e.health < enemyHealth) hurt = .18;
    String state;
    boolean sync = false;
    if (e.defeated()) state = "death";
    else if (e.posture.broken()) state = "stagger";
    else if (e.attackRun.running()) {
      state = e.attackRun.phase().name().toLowerCase(java.util.Locale.ROOT);
      enemy.play(
          e.attackRun.animation(),
          100
              + e.attackRun.serial() * 100
              + e.attackRun.strike() * 4
              + e.attackRun.phase().ordinal());
      enemy.sample(e.attackRun.progress());
      sync = true;
    } else if (hurt > 0) state = "hurt";
    else if (e.climbing || !e.grounded || Math.abs(e.x - enemyX) > .001) state = "run";
    else state = "idle";
    if (!sync) {
      enemy.play(e.definition.animations.get(state), state.hashCode());
      enemy.advance(dt);
    }
    enemyPose = state;
    enemyX = e.x;
    enemyHealth = e.health;
  }
}
