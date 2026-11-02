package com.lockin.game.content;

import com.lockin.game.CombatConfig;

/** Shared defense tuning; capacities/recovery rates are per-entity overrides. */
public final class DefenseDef {
  public final String id;
  public final CombatConfig config;
  public final double blockMultiplier, deflectMultiplier;

  public DefenseDef(String id, CombatConfig config, double block, double deflect) {
    this.id = id;
    this.config = config;
    blockMultiplier = block;
    deflectMultiplier = deflect;
  }

  public CombatConfig forEntity(double capacity, double recovery) {
    return new CombatConfig(
        config.deflectWindow,
        capacity,
        recovery,
        0,
        0,
        config.recoveryDelay,
        config.brokenDuration,
        config.spamStep,
        config.minimumDeflectWindow,
        config.spamRecovery,
        config.breakImmunity,
        config.deathblowRange);
  }
}
