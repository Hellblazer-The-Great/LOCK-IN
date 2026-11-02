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
        if (Math.abs(arriving.x + arriving.width / 2 - ladder.x()) > ladder.openingWidth() / 2)
          continue;
        float bottom = a.platform(ladder.bottom()).top();
        if (arriving.y + arriving.height < bottom - arriving.height - 40
            || arriving.y + arriving.height > bottom + 1) continue;
        for (int j = 0; j < state.enemies.length; j++) {
          Enemy standing = state.enemies[j];
          if (i == j || standing.defeated() || standing.climbing || !standing.grounded) continue;
          if (Math.abs(standing.y + standing.height - bottom) > 2
              || Math.abs(standing.x + standing.width / 2 - ladder.x())
                  > ladder.openingWidth() / 2 + standing.width) continue;
          if (state.navigators[j].yielding()) continue;
          float offset = ladder.openingWidth() / 2 + standing.width + 20;
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
    float x = center - e.width / 2;
    if (x < 0 || x + e.width > a.width || !SolidPhysics.supported(a, x, e.y, e.width, e.height))
      return false;
    if (Math.abs(SolidPhysics.horizontal(a, e.x, x, e.y, e.width, e.height) - x) > .1f)
      return false;
    for (int i = 0; i < all.length; i++) {
      Enemy other = all[i];
      if (i != index
          && !other.defeated()
          && CollisionManager.overlaps(
              x,
              e.y,
              x + e.width,
              e.y + e.height,
              other.x,
              other.y,
              other.x + other.width,
              other.y + other.height)) return false;
    }
    return true;
  }
}
