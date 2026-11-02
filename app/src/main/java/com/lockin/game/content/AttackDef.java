package com.lockin.game.content;

import java.util.*;

/**
 * Immutable attack timeline; a hit ID can commit its effects once per execution. Seconds/pixels.
 */
public final class AttackDef {
  public record Hit(
      String id,
      double start,
      double end,
      float forward,
      float top,
      float width,
      float height,
      String windupAnimation,
      String activeAnimation) {}

  public record Window(double start, double end) {
    public boolean includes(double t) {
      return t + 1e-7 >= start && t < end - 1e-7;
    }
  }

  public final String id, style, recoveryAnimation;
  public final int damage;
  public final double postureDamage, blockDamage, deflectDamage, duration, cooldown;
  public final boolean directional, blockable, deflectable, interruptOnHit;
  public final List<Hit> hits;
  public final List<Window> dashCancel;

  public AttackDef(
      String id,
      String style,
      int damage,
      double posture,
      double block,
      double deflect,
      double duration,
      double cooldown,
      boolean directional,
      boolean blockable,
      boolean deflectable,
      boolean interrupt,
      List<Hit> hits,
      List<Window> cancel,
      String recovery) {
    this.id = id;
    this.style = style;
    this.damage = damage;
    postureDamage = posture;
    blockDamage = block;
    deflectDamage = deflect;
    this.duration = duration;
    this.cooldown = cooldown;
    this.directional = directional;
    this.blockable = blockable;
    this.deflectable = deflectable;
    interruptOnHit = interrupt;
    this.hits = Collections.unmodifiableList(new ArrayList<>(hits));
    dashCancel = Collections.unmodifiableList(new ArrayList<>(cancel));
    recoveryAnimation = recovery;
  }

  public boolean canDash(double age) {
    for (Window w : dashCancel) if (w.includes(age)) return true;
    return false;
  }
}
