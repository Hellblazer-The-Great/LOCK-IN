package com.lockin.game;

public final class CollisionManager {
  private CollisionManager() {}

  public static void resolveWorld(Player p) {
    p.x = Math.max(0, Math.min(p.worldWidth() - p.width, p.x));
    if (p.y + p.height >= GameWorld.GROUND_Y) {
      p.y = GameWorld.GROUND_Y - p.height;
      p.velocityY = 0;
      p.grounded = true;
    } else p.grounded = false;
  }

  private static boolean verticalOverlap(Player p, Enemy e) {
    return p.y < e.y + e.height && p.y + p.height > e.y;
  }

  /**
   * Initial overlap/descending into a body: move the player to the nearest valid side. The enemy
   * never pushes a non-overlapping player; corpses have no body collision.
   */
  public static void resolveBodies(Player p, Enemy e) {
    p.x = Math.max(0, Math.min(p.worldWidth() - p.width, p.x));
    e.x = Math.max(0, Math.min(e.worldWidth() - e.width, e.x));
    if (p.defeated()
        || e.defeated()
        || !verticalOverlap(p, e)
        || p.x + p.width <= e.x
        || p.x >= e.x + e.width) return;
    float left = e.x - p.width, right = e.x + e.width;
    boolean leftValid = left >= 0, rightValid = right <= p.worldWidth() - p.width;
    if (leftValid && (!rightValid || Math.abs(p.x - left) <= Math.abs(right - p.x))) p.x = left;
    else p.x = right;
  }

  private static boolean verticalSweep(
      Player p, float oldY, float newY, Enemy e, float enter, float exit) {
    float dy = newY - oldY;
    if (dy == 0) return oldY < e.y + e.height && oldY + p.height > e.y;
    float a = (e.y - oldY - p.height) / dy;
    float b = (e.y + e.height - oldY) / dy;
    return Math.max(enter, Math.min(a, b)) < Math.min(exit, Math.max(a, b));
  }

  /**
   * Swept body intervals detect diagonal entry as well as horizontal crossing. Crossing entirely
   * above the enemy is allowed; descending through a corner is not.
   */
  public static void resolvePlayerMovement(Player p, Enemy e, float oldX, float oldY) {
    if (p.defeated() || e.defeated()) return;
    float dx = p.x - oldX;
    float t = -1;
    if (dx > 0 && oldX + p.width <= e.x && p.x + p.width > e.x) t = (e.x - oldX - p.width) / dx;
    else if (dx < 0 && oldX >= e.x + e.width && p.x < e.x + e.width)
      t = (e.x + e.width - oldX) / dx;
    if (t >= 0 && t <= 1) {
      float exit = dx > 0 ? (e.x + e.width - oldX) / dx : (e.x - oldX - p.width) / dx;
      if (verticalSweep(p, oldY, p.y, e, t, Math.min(1, exit)))
        p.x = dx > 0 ? e.x - p.width : e.x + e.width;
    }
    resolveBodies(p, e);
  }

  /** Clamp the pursuing enemy against the player's body, never displace the player. */
  public static void resolveEnemyMovement(Enemy e, Player p, float oldX) {
    if (p.defeated() || e.defeated() || !verticalOverlap(p, e)) return;
    if (oldX + e.width <= p.x && e.x + e.width > p.x) e.x = p.x - e.width;
    else if (oldX >= p.x + p.width && e.x < p.x + p.width) e.x = p.x + p.width;
    e.x = Math.max(0, Math.min(e.worldWidth() - e.width, e.x));
    resolveBodies(p, e);
  }

  /** Vertical pursuit must stop at a player's body rather than push the player off a ladder. */
  public static void resolveEnemyMovement(Enemy e, Player p, float oldX, float oldY) {
    if (!e.defeated() && !p.defeated() && e.x < p.x + p.width && e.x + e.width > p.x) {
      if (oldY + e.height <= p.y && e.y + e.height > p.y) {
        e.y = p.y - e.height;
        e.velocityY = 0;
      } else if (oldY >= p.y + p.height && e.y < p.y + p.height) {
        e.y = p.y + p.height;
        e.velocityY = 0;
      }
    }
    resolveEnemyMovement(e, p, oldX);
  }

  /** Each opponent stops against other living opponents rather than sharing a body. */
  public static void resolveEnemies(Enemy a, Enemy b, float oldX) {
    if (a.defeated() || b.defeated() || a.y >= b.y + b.height || a.y + a.height <= b.y) return;
    if (oldX + a.width <= b.x + .01f && a.x + a.width > b.x) a.x = b.x - a.width;
    else if (oldX >= b.x + b.width - .01f && a.x < b.x + b.width) a.x = b.x + b.width;
    else if (a.x < b.x + b.width && a.x + a.width > b.x) a.x = oldX;
    a.x = Math.max(0, Math.min(a.worldWidth() - a.width, a.x));
  }

  public static void resolveEnemies(Enemy a, Enemy b, float oldX, float oldY) {
    if (a.defeated() || b.defeated()) return;
    if (a.x < b.x + b.width && a.x + a.width > b.x) {
      if (oldY + a.height <= b.y + .001f && a.y + a.height > b.y) {
        a.y = b.y - a.height;
        a.velocityY = 0;
      } else if (oldY >= b.y + b.height - .001f && a.y < b.y + b.height) {
        a.y = b.y + b.height;
        a.velocityY = 0;
      }
    }
    resolveEnemies(a, b, oldX);
  }

  public static boolean overlaps(
      float l, float t, float r, float b, float l2, float t2, float r2, float b2) {
    return l < r2 && r > l2 && t < b2 && b > t2;
  }
}
