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
      Player p,
      AreaDefinition area,
      float oldX,
      float oldY,
      float dt,
      int vertical,
      int move,
      boolean jump) {
    if (area.continuous) {
      SolidPhysics.player(p, area, oldX, oldY, dt, vertical, move, jump);
      return;
    }
    p.dropTime = Math.max(0, p.dropTime - dt);
    if (vertical > 0
        && p.grounded
        && area.surface(p.x + p.width / 2, oldY + p.height) > 0
        && ladderAt(area, p.x + p.width / 2, oldY + p.height, vertical) < 0) {
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
      int ladder = ladderAt(area, p.x + p.width / 2, oldY + p.height, vertical);
      if (ladder >= 0) {
        p.climbing = true;
        p.ladder = ladder;
      }
    }
    if (p.climbing) {
      AreaDefinition.Ladder l = area.ladder(p.ladder);
      p.x = l.x() - p.width / 2;
      float top = area.platform(l.top()).top(), bottom = area.platform(l.bottom()).top();
      float feet = Math.max(top, Math.min(bottom, oldY + p.height + vertical * CLIMB_SPEED * dt));
      p.y = feet - p.height;
      p.velocityY = 0;
      p.grounded = false;
      if (feet <= top || feet >= bottom) {
        p.climbing = false;
        p.grounded = true;
      }
    } else {
      float feet = landing(area, p.x, p.width, oldY + p.height, p.y + p.height, p.dropTime > 0);
      if (p.velocityY >= 0
          && feet <= p.y + p.height
          && feet < p.y + p.height + .001f
          && (feet < p.y + p.height || supported(area, p.x, p.width, feet, p.dropTime > 0))) {
        p.y = feet - p.height;
        p.velocityY = 0;
        p.grounded = true;
      } else p.grounded = false;
    }
    p.x = Math.max(0, Math.min(GameWorld.WIDTH - p.width, p.x));
    if (p.y + p.height >= GameWorld.GROUND_Y) {
      p.y = GameWorld.GROUND_Y - p.height;
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
    float feet = landing(area, e.x, e.width, oldY + e.height, e.y + e.height, false);
    e.grounded = e.velocityY >= 0 && supported(area, e.x, e.width, feet, false);
    if (e.grounded) {
      e.y = feet - e.height;
      e.velocityY = 0;
    }
    if (e.y + e.height >= GameWorld.GROUND_Y) {
      e.y = GameWorld.GROUND_Y - e.height;
      e.velocityY = 0;
      e.grounded = true;
    }
  }
}
