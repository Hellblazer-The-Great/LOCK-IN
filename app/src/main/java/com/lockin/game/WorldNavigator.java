package com.lockin.game;

/** Authored surface routing with committed traversal, aperture jumps and bounded exit yielding. */
public final class WorldNavigator {
  private AreaDefinition.Link active;
  private boolean airborne, exiting;
  private float exitX, yieldX, yieldTime;
  private int[] queue, via;
  private double stuckTime;
  private float previousX, previousY;

  public void reset() {
    active = null;
    airborne = exiting = false;
    yieldTime = 0;
    stuckTime = 0;
  }

  public void yieldTo(float x) {
    if (yieldTime <= 0) {
      yieldX = x;
      yieldTime = 1.5f;
    }
  }

  public boolean yielding() {
    return yieldTime > 0;
  }

  public boolean update(Enemy e, Player p, AreaDefinition a, float dt) {
    if (e.defeated() || e.posture.broken()) {
      reset();
      e.climbing = false;
      return false;
    }
    if (e.state() == Enemy.State.ATTACK_ACTIVE
        || e.state() == Enemy.State.ATTACK_WINDUP
        || e.state() == Enemy.State.ATTACK_RECOVERY) return false;
    if (yieldTime > 0) {
      yieldTime = Math.max(0, yieldTime - dt);
      e.climbing = false;
      walk(e, yieldX, e.speed, dt);
      if (Math.abs(e.x + e.width / 2 - yieldX) < 2) yieldTime = 0;
      return true;
    }
    if (queue == null || queue.length != a.platformCount()) {
      queue = new int[a.platformCount()];
      via = new int[a.platformCount()];
    }
    float center = e.x + e.width / 2, feet = e.y + e.height;
    if (active != null) {
      if (Math.abs(e.x - previousX) + Math.abs(e.y - previousY) < .001f) stuckTime += dt;
      else stuckTime = 0;
      // Collision may temporarily obstruct a route. Replan rather than keeping a stale commitment
      // forever.
      if (stuckTime > 2.5) {
        reset();
        e.climbing = false;
      }
    }
    previousX = e.x;
    previousY = e.y;
    int from = a.surface(center, feet), to = a.surface(p.x + p.width / 2, p.y + p.height);
    if (active != null && active.travel() == AreaDefinition.Travel.JUMP && airborne && e.grounded) {
      active = null;
      airborne = false;
    }
    if (active == null) {
      if (!e.detects(p) || !e.canChase()) return false;
      if (from == to) {
        if (!e.grounded) return false;
        int dir = p.x + p.width / 2 > center ? 1 : -1;
        // A floor aperture is traversed by a physical jump, never by ignoring the floor.
        for (int i = 0; i < a.ladderCount(); i++) {
          var l = a.ladder(i);
          if (l.top() != from) continue;
          float near = l.x() - dir * (l.openingWidth() / 2 + e.width / 2 + 6);
          if (Math.abs(center - near) < 12 && dir * (p.x + p.width / 2 - l.x()) > 0) {
            active =
                new AreaDefinition.Link(
                    from,
                    to,
                    AreaDefinition.Travel.JUMP,
                    near,
                    l.x() + dir * (l.openingWidth() / 2 + e.width / 2 + 6));
            break;
          }
        }
        if (active == null) return false;
      } else {
        java.util.Arrays.fill(via, -1);
        int head = 0, tail = 0;
        queue[tail++] = from;
        via[from] = -2;
        while (head < tail && via[to] == -1) {
          int node = queue[head++];
          for (int i = 0; i < a.linkCount(); i++) {
            var edge = a.link(i);
            if (edge.from() == node && via[edge.to()] == -1) {
              via[edge.to()] = i;
              queue[tail++] = edge.to();
            }
          }
        }
        if (via[to] < 0) return true;
        int node = to;
        while (a.link(via[node]).from() != from) node = a.link(via[node]).from();
        active = a.link(via[node]);
      }
    }
    if (active.travel() == AreaDefinition.Travel.LADDER) {
      float target = a.platform(active.to()).top();
      if (exiting) {
        e.climbing = true;
        e.y = target - e.height;
        e.velocityY = 0;
        walk(e, exitX, e.speed, dt);
        if (Math.abs(e.x + e.width / 2 - exitX) < 1) {
          e.climbing = false;
          e.grounded = true;
          active = null;
          exiting = false;
        }
      } else if (!e.climbing
          && Math.abs(center - active.launch()) > a.ladderFor(active).entryRadius()) {
        walk(e, active.launch(), e.speed, dt);
      } else {
        e.climbing = true;
        e.grounded = false;
        e.velocityY = 0;
        if (Math.abs(center - active.launch()) > 1) {
          walk(e, active.launch(), e.climbSpeed, dt);
          return true;
        }
        e.x = active.launch() - e.width / 2;
        e.y += Math.copySign(Math.min(Math.abs(target - feet), e.climbSpeed * dt), target - feet);
        if (Math.abs(e.y + e.height - target) < .1f) {
          e.y = target - e.height;
          if (active.to() == 0 || target > a.platform(active.from()).top()) {
            e.climbing = false;
            e.grounded = SolidPhysics.supported(a, e.x, e.y, e.width, e.height);
            active = null;
          } else {
            var deck = a.platform(active.to());
            float offset = a.ladderFor(active).openingWidth() / 2 + e.width / 2 + 2;
            float onward =
                active.to() == to
                    ? p.x + p.width / 2
                    : pathLaunch(a, active.to(), to, p.x + p.width / 2);
            boolean right = onward >= center;
            exitX = center + (right ? offset : -offset);
            if (exitX + e.width / 2 > deck.right() || exitX - e.width / 2 < deck.left())
              exitX = center + (right ? -offset : offset);
            exiting = true;
          }
        }
      }
    } else if (active.travel() == AreaDefinition.Travel.WALK) {
      walk(e, active.destination(), e.speed, dt);
      if (Math.abs(center - active.destination()) < 3) active = null;
    } else if (active.travel() == AreaDefinition.Travel.JUMP) {
      if (!airborne) {
        if (Math.abs(center - active.launch()) > 2) walk(e, active.launch(), e.speed, dt);
        else if (e.grounded) {
          e.velocityY = -e.jumpSpeed;
          e.grounded = false;
          airborne = true;
        }
      } else walk(e, active.destination(), e.airSpeed, dt);
    } else {
      walk(e, active.launch(), e.speed, dt);
      if (e.grounded && from == active.to()) active = null;
    }
    return true;
  }

  private float pathLaunch(AreaDefinition a, int from, int to, float fallback) {
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
    if (via[to] < 0) return fallback;
    int node = to;
    while (a.link(via[node]).from() != from) node = a.link(via[node]).from();
    return a.link(via[node]).launch();
  }

  private static void walk(Enemy e, float center, float speed, float dt) {
    float dx = center - (e.x + e.width / 2);
    if (dx != 0) e.facingRight = dx > 0;
    e.x += e.approachDelta(dx, speed, dt);
  }
}
