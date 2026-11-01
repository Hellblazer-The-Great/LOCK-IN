package com.lockin.game;

/**
 * A descending arrival gets space: the occupant yields to one deterministic safe floor position.
 */
public final class LadderTraffic {
  public void update(AreaState state, float dt) {
    var a = state.definition;
    for (int i = 0; i < state.enemies.length; i++) {
      Enemy arriving = state.enemies[i];
      if (arriving.defeated() || !arriving.climbing) continue;
      for (int l = 0; l < a.ladderCount(); l++) {
        var ladder = a.ladder(l);
        if (Math.abs(arriving.x + Enemy.WIDTH / 2 - ladder.x()) > WorldDefinition.OPENING_WIDTH / 2)
          continue;
        float bottom = a.platform(ladder.bottom()).top();
        if (arriving.y + Enemy.HEIGHT < bottom - Enemy.HEIGHT - 40
            || arriving.y + Enemy.HEIGHT > bottom + 1) continue;
        for (int j = 0; j < state.enemies.length; j++) {
          Enemy standing = state.enemies[j];
          if (i == j || standing.defeated() || standing.climbing || !standing.grounded) continue;
          if (Math.abs(standing.y + Enemy.HEIGHT - bottom) > 2
              || Math.abs(standing.x + Enemy.WIDTH / 2 - ladder.x())
                  > WorldDefinition.OPENING_WIDTH / 2 + Enemy.WIDTH) continue;
          if (state.navigators[j].yielding()) continue;
          float offset = WorldDefinition.OPENING_WIDTH / 2 + Enemy.WIDTH + 20;
          float right = ladder.x() + offset, left = ladder.x() - offset;
          boolean preferRight = (j & 1) == 0;
          float target = preferRight ? right : left;
          if (!safe(a, standing, target, state.enemies, j)) target = preferRight ? left : right;
          if (safe(a, standing, target, state.enemies, j)) state.navigators[j].yieldTo(target);
        }
      }
    }
  }

  private boolean safe(AreaDefinition a, Enemy e, float center, Enemy[] all, int index) {
    float x = center - Enemy.WIDTH / 2;
    if (x < 0
        || x + Enemy.WIDTH > a.width
        || !SolidPhysics.supported(a, x, e.y, Enemy.WIDTH, Enemy.HEIGHT)) return false;
    if (Math.abs(SolidPhysics.horizontal(a, e.x, x, e.y, Enemy.WIDTH, Enemy.HEIGHT) - x) > .1f)
      return false;
    for (int i = 0; i < all.length; i++) {
      Enemy other = all[i];
      if (i != index
          && !other.defeated()
          && CollisionManager.overlaps(
              x,
              e.y,
              x + Enemy.WIDTH,
              e.y + Enemy.HEIGHT,
              other.x,
              other.y,
              other.x + Enemy.WIDTH,
              other.y + Enemy.HEIGHT)) return false;
    }
    return true;
  }
}
