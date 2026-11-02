package com.lockin.game;

import com.lockin.game.content.*;

/** Mutable state is retained for the entire run, including inactive areas. */
public final class AreaState {
  public final LevelDef level;
  public final boolean[] collected;
  public final AreaDefinition definition;
  public final Enemy[] enemies;
  public final EnemyNavigator[] navigators;

  public AreaState(AreaDefinition definition, CombatConfig config, int hp) {
    level = null;
    collected = new boolean[0];
    this.definition = definition;
    enemies = new Enemy[definition.spawnCount()];
    navigators = new EnemyNavigator[enemies.length];
    for (int i = 0; i < enemies.length; i++) {
      enemies[i] = new Enemy(config, hp);
      navigators[i] = new EnemyNavigator();
    }
    reset();
  }

  public AreaState(LevelDef level, Content content) {
    this.level = level;
    definition = level.area;
    collected = new boolean[level.pickups.size()];
    enemies = new Enemy[level.enemies.size()];
    navigators = new EnemyNavigator[enemies.length];
    for (int i = 0; i < enemies.length; i++) {
      enemies[i] = new Enemy(content.entities.get(level.enemies.get(i).entity()), content);
      navigators[i] = new EnemyNavigator();
    }
    reset();
  }

  public void reset() {
    java.util.Arrays.fill(collected, false);
    for (int i = 0; i < enemies.length; i++) {
      Enemy e = enemies[i];
      e.terrain = definition;
      e.reset();
      AreaDefinition.Spawn s = definition.spawn(i);
      e.x = s.x();
      e.y = definition.platform(s.surface()).top() - e.height;
      if (level != null) {
        var spawn = level.enemies.get(i);
        e.x = spawn.x();
        e.y = spawn.feet() - e.height;
        e.facingRight = spawn.right();
        e.seed(spawn.id().hashCode());
      }
      navigators[i].reset();
    }
  }
}
