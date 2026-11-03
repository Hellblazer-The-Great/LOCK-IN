package com.lockin.game;

import com.lockin.game.content.AttackDef;
import java.util.Arrays;

/** One actor's authoritative attack clock. Visual frames never enable damage. */
public final class AttackRun {
  public enum Phase {
    WINDUP,
    ACTIVE,
    RECOVERY,
    DONE
  }

  private AttackDef definition;
  private final boolean[] consumed = new boolean[32];
  private double age, previousAge;
  private boolean running;
  private long serial;

  public AttackDef definition() {
    return definition;
  }

  public double age() {
    return age;
  }

  public long serial() {
    return serial;
  }

  public boolean running() {
    return running;
  }

  public void start(AttackDef next) {
    definition = next;
    age = 0;
    previousAge = -1e-9;
    running = true;
    serial++;
    Arrays.fill(consumed, false);
  }

  public void cancel() {
    running = false;
    Arrays.fill(consumed, true);
  }

  public void reset() {
    cancel();
    definition = null;
    age = previousAge = 0;
    serial = 0;
  }

  public void advance(double dt) {
    if (!running) return;
    previousAge = age;
    age = Math.min(definition.duration, age + dt);
    if (age + 1e-7 >= definition.duration) running = false;
  }

  public int strike() {
    if (definition == null) return 0;
    for (int i = 0; i < definition.hits.size(); i++)
      if (age < definition.hits.get(i).end() - 1e-7) return i;
    return definition.hits.size() - 1;
  }

  public Phase phase() {
    if (!running) return Phase.DONE;
    var h = definition.hits.get(strike());
    if (age + 1e-7 < h.start()) return Phase.WINDUP;
    if (age < h.end() - 1e-7) return Phase.ACTIVE;
    return Phase.RECOVERY;
  }

  public boolean canHit() {
    return phase() == Phase.ACTIVE && !consumed[strike()];
  }

  public void consume() {
    consumed[strike()] = true;
  }

  public AttackDef.Hit hit() {
    return definition.hits.get(strike());
  }

  public float progress() {
    if (definition == null) return 0;
    var h = hit();
    double start, end;
    if (phase() == Phase.WINDUP) {
      start = strike() == 0 ? 0 : definition.hits.get(strike() - 1).end();
      end = h.start();
    } else if (phase() == Phase.ACTIVE) {
      start = h.start();
      end = h.end();
    } else {
      start = definition.hits.get(definition.hits.size() - 1).end();
      end = definition.duration;
    }
    return Vitals.fraction(age - start, end - start);
  }

  public String animation() {
    if (definition == null) return "";
    if (phase() == Phase.WINDUP) return hit().windupAnimation();
    if (phase() == Phase.ACTIVE) return hit().activeAnimation();
    return definition.recoveryAnimation;
  }

  /** Named events are queryable once per tick/strike, including intervals skipped by a render. */
  public boolean event(String name, int strike) {
    if (definition == null || strike < 0 || strike >= definition.hits.size()) return false;
    double at;
    if (name.equals("hitbox_on")) at = definition.hits.get(strike).start();
    else if (name.equals("hitbox_off")) at = definition.hits.get(strike).end();
    else if (name.equals("complete")) at = definition.duration;
    else return false;
    return previousAge < at && age + 1e-7 >= at;
  }

  /** Values belong to the current hit, with attack-level fallback for old packs. */
  public int damage() {
    return hit().damage() < 0 ? definition.damage : hit().damage();
  }
  public double postureDamage() {
    return hit().postureDamage() < 0 ? definition.postureDamage : hit().postureDamage();
  }
  public double blockDamage() {
    return hit().blockDamage() < 0 ? definition.blockDamage : hit().blockDamage();
  }
  public double deflectDamage() {
    return hit().deflectDamage() < 0 ? definition.deflectDamage : hit().deflectDamage();
  }

  public float left(float x, float width, boolean right) {
    var h = hit();
    boolean forward = !definition.directional || right;
    return forward ? x + width + h.forward() : x - h.forward() - h.width();
  }
}
