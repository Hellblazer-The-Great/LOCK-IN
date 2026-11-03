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
      String activeAnimation,
      int damage,
      double postureDamage,
      double blockDamage,
      double deflectDamage) {
    /** Original constructor retained for fixtures and existing Java callers. */
    public Hit(String id, double start, double end, float forward, float top,
               float width, float height, String windupAnimation, String activeAnimation) {
      this(id, start, end, forward, top, width, height, windupAnimation, activeAnimation,
          -1, -1, -1, -1);
    }
  }

  public record Window(double start, double end) {
    public boolean includes(double t) {
      return t + 1e-7 >= start && t < end - 1e-7;
    }
  }

  public record Motion(double start,double end,float distance,float windup,float active,float recovery) {
    public static final Motion NONE=new Motion(0,1,0,1,1,1);
    public static Motion parse(Json.Node n,double duration) {
      n.only("start","end","distance","windupMultiplier","activeMultiplier","recoveryMultiplier");
      double start=n.get("start").range(0,duration);
      double end=n.get("end").range(start+1.0/120,duration);
      return new Motion(start,end,n.f("distance",-2000,2000),
          n.has("windupMultiplier")?n.f("windupMultiplier",0,5):1,
          n.has("activeMultiplier")?n.f("activeMultiplier",0,5):1,
          n.has("recoveryMultiplier")?n.f("recoveryMultiplier",0,5):1);
    }
  }
  public final Motion motion;
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
    this(id,style,damage,posture,block,deflect,duration,cooldown,directional,blockable,
        deflectable,interrupt,hits,cancel,recovery,Motion.NONE);
  }

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
      String recovery, Motion motion) {
    this.motion=motion;
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
