package com.lockin.game;

/**
 * Swept axis-separated AABB terrain. Authored ladder apertures are real gaps, never ignored floors.
 */
public final class SolidPhysics {
  private SolidPhysics() {}

  public static float horizontal(AreaDefinition a, float oldX, float x, float y, float w, float h) {
    float result = Math.max(0, Math.min(a.width - w, x));
    for (int i = 0; i < a.solidCount(); i++) {
      var s = a.solid(i);
      if (y + h <= s.top() + .001f || y >= s.bottom() - .001f) continue;
      if (x > oldX && oldX + w <= s.left() + .001f && result + w > s.left())
        result = Math.min(result, s.left() - w);
      else if (x < oldX && oldX >= s.right() - .001f && result < s.right())
        result = Math.max(result, s.right());
    }
    return result;
  }

  public static float vertical(AreaDefinition a, float x, float oldY, float y, float w, float h) {
    float result = Math.max(0, Math.min(a.height - h, y));
    for (int i = 0; i < a.solidCount(); i++) {
      var s = a.solid(i);
      if (x + w <= s.left() + .001f || x >= s.right() - .001f) continue;
      if (y >= oldY && oldY + h <= s.top() + .001f && result + h > s.top())
        result = Math.min(result, s.top() - h);
      else if (y < oldY && oldY >= s.bottom() - .001f && result < s.bottom())
        result = Math.max(result, s.bottom());
    }
    return result;
  }

  public static boolean supported(AreaDefinition a, float x, float y, float w, float h) {
    for (int i = 0; i < a.solidCount(); i++) {
      var s = a.solid(i);
      if (x + w > s.left() + .001f && x < s.right() - .001f && Math.abs(y + h - s.top()) < .02f)
        return true;
    }
    return false;
  }

  public static void player(
      Player p,
      AreaDefinition a,
      float oldX,
      float oldY,
      float dt,
      int vertical,
      int move,
      boolean jump) {
    if (p.dashing() || p.staggered() || p.deathblowing() || jump) p.climbing = false;
    if (!p.climbing
        && !p.dashing()
        && !p.staggered()
        && !p.deathblowing()
        && !jump
        && vertical != 0) {
      int id = ladderAt(a, p.x + Player.WIDTH / 2, oldY + Player.HEIGHT, vertical);
      if (id >= 0) {
        p.climbing = true;
        p.ladder = id;
      }
    }
    if (p.climbing) {
      var l = a.ladder(p.ladder);
      float top = a.platform(l.top()).top(), bottom = a.platform(l.bottom()).top();
      float oldFeet = oldY + Player.HEIGHT;
      if (move != 0 && oldFeet <= top + .02f) {
        // Pull over the lip under explicit horizontal input, with feet on the floor plane.
        p.y = top - Player.HEIGHT;
        p.velocityY = 0;
        p.x = horizontal(a, oldX, p.x, p.y, Player.WIDTH, Player.HEIGHT);
        if (Math.abs(p.x + Player.WIDTH / 2 - l.x())
            >= WorldDefinition.OPENING_WIDTH / 2 + Player.WIDTH / 2) {
          p.climbing = false;
          p.grounded = supported(a, p.x, p.y, Player.WIDTH, Player.HEIGHT);
        }
        return;
      }
      if (move != 0) {
        p.climbing = false;
      } else {
        float desired = l.x() - Player.WIDTH / 2;
        p.x =
            oldX
                + Math.copySign(
                    Math.min(Math.abs(desired - oldX), TerrainPhysics.CLIMB_SPEED * dt),
                    desired - oldX);
        if (Math.abs(p.x - desired) > .1f) {
          p.y = oldY;
          p.velocityY = 0;
          p.grounded = false;
          return;
        }
        float feet =
            Math.max(top, Math.min(bottom, oldFeet + vertical * TerrainPhysics.CLIMB_SPEED * dt));
        p.y = vertical(a, p.x, oldY, feet - Player.HEIGHT, Player.WIDTH, Player.HEIGHT);
        p.velocityY = 0;
        p.grounded = false;
        if (feet >= bottom && vertical > 0) {
          p.climbing = false;
          p.grounded = supported(a, p.x, p.y, Player.WIDTH, Player.HEIGHT);
        }
        return;
      }
    }
    p.x = horizontal(a, oldX, p.x, oldY, Player.WIDTH, Player.HEIGHT);
    float resolved = vertical(a, p.x, oldY, p.y, Player.WIDTH, Player.HEIGHT);
    if (Math.abs(resolved - p.y) > .0001f) p.velocityY = 0;
    p.y = resolved;
    p.grounded = supported(a, p.x, p.y, Player.WIDTH, Player.HEIGHT);
  }

  public static int ladderAt(AreaDefinition a, float center, float feet, int vertical) {
    for (int i = 0; i < a.ladderCount(); i++) {
      var l = a.ladder(i);
      float top = a.platform(l.top()).top(), bottom = a.platform(l.bottom()).top();
      // Down entry permits the actor to step into the aperture from a supported lip.
      if (Math.abs(center - l.x()) <= WorldDefinition.OPENING_WIDTH / 2 + 4
          && feet >= top - .02f
          && feet <= bottom + .02f
          && !(vertical < 0 && feet <= top)
          && !(vertical > 0 && feet >= bottom)) {
        if (Math.abs(center - l.x()) <= WorldDefinition.ENTRY_RADIUS
            || (vertical > 0 && Math.abs(feet - top) < .02f)) return i;
      }
    }
    return -1;
  }

  public static void enemy(Enemy e, AreaDefinition a, float oldX, float oldY, float dt) {
    if (e.defeated()) return;
    e.x = horizontal(a, oldX, e.x, oldY, Enemy.WIDTH, Enemy.HEIGHT);
    if (e.climbing) {
      float y = vertical(a, e.x, oldY, e.y, Enemy.WIDTH, Enemy.HEIGHT);
      e.y = y;
      e.velocityY = 0;
      return;
    }
    e.velocityY += GameWorld.GRAVITY * dt;
    float desired = e.y + e.velocityY * dt;
    float resolved = vertical(a, e.x, oldY, desired, Enemy.WIDTH, Enemy.HEIGHT);
    if (Math.abs(resolved - desired) > .0001f) e.velocityY = 0;
    e.y = resolved;
    e.grounded = supported(a, e.x, e.y, Enemy.WIDTH, Enemy.HEIGHT);
  }
}
