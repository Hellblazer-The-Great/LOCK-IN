package com.lockin.game.content;

import com.lockin.game.CombatConfig;
import java.util.*;

/** Shared definition only; mutable HP, position, posture and timers belong to each actor. */
public final class EntityDef {
  public final String id, kind, attack, behavior, combo;
  public final int maxHealth;
  public final float width, height, speed, acceleration, jumpSpeed, gravity, climbSpeed, airSpeed;
  public final double dashDuration,
      dashSpeed,
      dashCooldown,
      iframeStart,
      iframeEnd,
      deathblowDuration;
  public final CombatConfig combat;
  public final double blockPostureMultiplier, deflectPostureMultiplier;
  public final Map<String, String> animations;

  public EntityDef(
      Json.Node n,
      CombatConfig combat,
      Map<String, String> animations,
      double block,
      double deflect) {
    blockPostureMultiplier = block;
    deflectPostureMultiplier = deflect;
    id = n.str("id");
    kind = n.str("kind");
    attack = n.str("attack");
    behavior = n.str("behavior", "");
    combo = n.str("combo", "");
    maxHealth = n.get("maxHealth").integer(1, 1000000000);
    width = n.f("width", 1, 500);
    height = n.f("height", 1, 500);
    speed = n.f("speed", 0, 5000);
    acceleration = n.f("acceleration", 0, 100000);
    jumpSpeed = n.f("jumpSpeed", 0, 5000);
    gravity = n.f("gravity", 1, 20000);
    climbSpeed = n.f("climbSpeed", 1, 2000);
    airSpeed = n.f("airSpeed", 0, 5000);
    Json.Node d = n.get("dash");
    d.only("duration", "speed", "cooldown", "iframeStart", "iframeEnd");
    dashDuration = d.get("duration").range(.008333, 10);
    dashSpeed = d.get("speed").range(0, 10000);
    dashCooldown = d.get("cooldown").range(dashDuration, 60);
    iframeStart = d.get("iframeStart").range(0, dashDuration);
    iframeEnd = d.get("iframeEnd").range(iframeStart, dashDuration);
    deathblowDuration = n.get("deathblowDuration").range(.008333, 10);
    this.combat = combat;
    this.animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
  }
}
