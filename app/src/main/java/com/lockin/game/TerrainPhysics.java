package com.lockin.game;

/** Swept descending surfaces; elevated decks are intentionally one-way, floor is always solid. */
public final class TerrainPhysics {
  private TerrainPhysics() {}

  public static final float CLIMB_SPEED = 140;

  public static float landing(
      AreaDefinition area, float x, float width, float oldFeet, float feet, boolean dropping) {
    float result = feet;
    if (feet < oldFeet) return result;
    for (int i = 0; i < area.platformCount(); i++) {
      AreaDefinition.Platform s = area.platform(i);
      if (dropping && s.oneWay()) continue;
      if (x + width > s.left() && x < s.right() && oldFeet <= s.top() + .01f && feet >= s.top())
        result = Math.min(result, s.top());
    }
    return result;
  }

  public static int ladderAt(AreaDefinition area, float center, float feet, int vertical) {
    for (int i = 0; i < area.ladderCount(); i++) {
      AreaDefinition.Ladder l = area.ladder(i);
      float top = area.platform(l.top()).top(), bottom = area.platform(l.bottom()).top();
      if (Math.abs(center - l.x()) <= 26
          && feet >= top - .01f
          && feet <= bottom + .01f
          && !(vertical > 0 && feet >= bottom)
          && !(vertical < 0 && feet <= top)) return i;
    }
    return -1;
  }

  public static void player(
      Player p, AreaDefinition area, float oldY, float dt, int vertical, int move, boolean jump) {
    p.dropTime = Math.max(0, p.dropTime - dt);
    if (vertical > 0
        && p.grounded
        && area.surface(p.x + Player.WIDTH / 2, oldY + Player.HEIGHT) > 0
        && ladderAt(area, p.x + Player.WIDTH / 2, oldY + Player.HEIGHT, vertical) < 0) {
      p.dropTime = .25f;
      p.grounded = false;
      p.y += 2;
      p.velocityY = Math.max(40, p.velocityY);
    }
    if (p.dashing() || p.staggered() || p.deathblowing() || jump || move != 0) p.climbing = false;
    if (!p.dashing()
        && !p.staggered()
        && !p.deathblowing()
        && !jump
        && move == 0
        && vertical != 0) {
      int ladder = ladderAt(area, p.x + Player.WIDTH / 2, oldY + Player.HEIGHT, vertical);
      if (ladder >= 0) {
        p.climbing = true;
        p.ladder = ladder;
      }
    }
    if (p.climbing) {
      AreaDefinition.Ladder l = area.ladder(p.ladder);
      p.x = l.x() - Player.WIDTH / 2;
      float top = area.platform(l.top()).top(), bottom = area.platform(l.bottom()).top();
      float feet =
          Math.max(top, Math.min(bottom, oldY + Player.HEIGHT + vertical * CLIMB_SPEED * dt));
      p.y = feet - Player.HEIGHT;
      p.velocityY = 0;
      p.grounded = false;
      if (feet <= top || feet >= bottom) {
        p.climbing = false;
        p.grounded = true;
      }
    } else {
      float feet =
          landing(
              area, p.x, Player.WIDTH, oldY + Player.HEIGHT, p.y + Player.HEIGHT, p.dropTime > 0);
      if (p.velocityY >= 0
          && feet <= p.y + Player.HEIGHT
          && feet < p.y + Player.HEIGHT + .001f
          && (feet < p.y + Player.HEIGHT
              || supported(area, p.x, Player.WIDTH, feet, p.dropTime > 0))) {
        p.y = feet - Player.HEIGHT;
        p.velocityY = 0;
        p.grounded = true;
      } else p.grounded = false;
    }
    p.x = Math.max(0, Math.min(GameWorld.WIDTH - Player.WIDTH, p.x));
    if (p.y + Player.HEIGHT >= GameWorld.GROUND_Y) {
      p.y = GameWorld.GROUND_Y - Player.HEIGHT;
      p.velocityY = 0;
      p.grounded = true;
    }
  }

  private static boolean supported(
      AreaDefinition area, float x, float width, float feet, boolean drop) {
    for (int i = 0; i < area.platformCount(); i++) {
      var s = area.platform(i);
      if (!(drop && s.oneWay())
          && x + width > s.left()
          && x < s.right()
          && Math.abs(feet - s.top()) < .01f) return true;
    }
    return false;
  }

  public static void enemy(Enemy e, AreaDefinition area, float oldY, float dt) {
    if (e.defeated()) return;
    if (e.climbing) return;
    e.velocityY += GameWorld.GRAVITY * dt;
    e.y += e.velocityY * dt;
    float feet = landing(area, e.x, Enemy.WIDTH, oldY + Enemy.HEIGHT, e.y + Enemy.HEIGHT, false);
    e.grounded = e.velocityY >= 0 && supported(area, e.x, Enemy.WIDTH, feet, false);
    if (e.grounded) {
      e.y = feet - Enemy.HEIGHT;
      e.velocityY = 0;
    }
    if (e.y + Enemy.HEIGHT >= GameWorld.GROUND_Y) {
      e.y = GameWorld.GROUND_Y - Enemy.HEIGHT;
      e.velocityY = 0;
      e.grounded = true;
    }
  }
}
