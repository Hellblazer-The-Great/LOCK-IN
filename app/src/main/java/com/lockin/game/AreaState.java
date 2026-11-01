package com.lockin.game;

/** Mutable state is retained for the entire run, including inactive areas. */
public final class AreaState {
  public final AreaDefinition definition;
  public final Enemy[] enemies;
  public final EnemyNavigator[] navigators;

  public AreaState(AreaDefinition definition, CombatConfig config, int hp) {
    this.definition = definition;
    enemies = new Enemy[definition.spawnCount()];
    navigators = new EnemyNavigator[enemies.length];
    for (int i = 0; i < enemies.length; i++) {
      enemies[i] = new Enemy(config, hp);
      navigators[i] = new EnemyNavigator();
    }
    reset();
  }

  public void reset() {
    for (int i = 0; i < enemies.length; i++) {
      Enemy e = enemies[i];
      e.terrain=definition;
      e.reset();
      AreaDefinition.Spawn s = definition.spawn(i);
      e.x = s.x();
      e.y = definition.platform(s.surface()).top() - Enemy.HEIGHT;
      navigators[i].reset();
    }
  }
}
