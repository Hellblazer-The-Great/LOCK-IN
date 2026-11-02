package com.lockin.game;

/** Small deterministic breadth-first surface graph. A committed edge is held until landing. */
public final class EnemyNavigator {
  private final WorldNavigator world = new WorldNavigator();

  public void yieldTo(float x) {
    world.yieldTo(x);
  }

  public boolean yielding() {
    return world.yielding();
  }

  private AreaDefinition.Link active;
  private boolean airborne;
  private final int[] queue = new int[8], via = new int[8];

  public void reset() {
    active = null;
    airborne = false;
    world.reset();
  }

  public boolean update(Enemy e, Player p, AreaDefinition a, float dt) {
    if (a.continuous) return world.update(e, p, a, dt);
    if (e.defeated()
        || e.posture.broken()
        || e.state() == Enemy.State.ATTACK_WINDUP
        || e.state() == Enemy.State.ATTACK_ACTIVE
        || e.state() == Enemy.State.ATTACK_RECOVERY) {
      e.climbing = false;
      return false;
    }
    int from = a.surface(e.x + e.width / 2, e.y + e.height),
        to = a.surface(p.x + p.width / 2, p.y + p.height);
    if (active != null
        && Math.abs(e.y + e.height - a.platform(active.to()).top()) < .1f
        && e.grounded) reset();
    if (active != null && airborne && e.grounded) {
      reset();
      return true;
    }
    if (active == null) {
      if (from == to || !e.detects(p)) return false;
      java.util.Arrays.fill(via, -1);
      int head = 0, tail = 0;
      queue[tail++] = from;
      via[from] = -2;
      while (head < tail && via[to] == -1) {
        int node = queue[head++];
        for (int i = 0; i < a.linkCount(); i++) {
          var link = a.link(i);
          if (link.from() == node && via[link.to()] == -1) {
            via[link.to()] = i;
            queue[tail++] = link.to();
          }
        }
      }
      if (via[to] < 0) return true;
      int node = to;
      while (a.link(via[node]).from() != from) node = a.link(via[node]).from();
      active = a.link(via[node]);
    }
    float center = e.x + e.width / 2;
    if (active.travel() == AreaDefinition.Travel.LADDER) {
      float dx = active.launch() - center;
      if (Math.abs(dx) > 1 && !e.climbing) walk(e, dx, dt);
      else {
        e.x = active.launch() - e.width / 2;
        e.climbing = true;
        e.velocityY = 0;
        e.grounded = false;
        float target = a.platform(active.to()).top();
        float feet = e.y + e.height;
        float delta =
            Math.copySign(
                Math.min(Math.abs(target - feet), TerrainPhysics.CLIMB_SPEED * dt), target - feet);
        e.y += delta;
        if (Math.abs(target - e.y - e.height) < .1f) {
          e.y = target - e.height;
          e.climbing = false;
          e.grounded = true;
          reset();
        }
      }
    } else if (active.travel() == AreaDefinition.Travel.JUMP) {
      if (!airborne) {
        float dx = active.launch() - center;
        if (Math.abs(dx) > 2) walk(e, dx, dt);
        else if (e.grounded) {
          e.velocityY = -590;
          e.grounded = false;
          airborne = true;
        }
      } else {
        float dx = active.destination() - center;
        e.x += Math.copySign(Math.min(Math.abs(dx), 250 * dt), dx);
        if (dx != 0) e.facingRight = dx > 0;
      }
    } else {
      walk(e, active.launch() - center, dt);
    }
    return true;
  }

  private void walk(Enemy e, float dx, float dt) {
    if (dx != 0) e.facingRight = dx > 0;
    e.x += Math.copySign(Math.min(Math.abs(dx), e.speed * dt), dx);
    e.x = Math.max(0, Math.min(GameWorld.WIDTH - e.width, e.x));
  }
}
