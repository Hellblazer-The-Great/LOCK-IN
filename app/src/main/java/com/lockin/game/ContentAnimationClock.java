package com.lockin.game;

import com.lockin.game.content.*;

/** Pure presentation clock. Timeline sampling uses combat phase progress, never drives combat. */
public final class ContentAnimationClock {
  private final Content content;
  private AnimationDef clip;
  private String requested = "";
  private long key;
  private boolean entered;
  private double age, previous;

  public ContentAnimationClock(Content content) {
    this.content = content;
  }

  public AnimationDef clip() {
    return clip;
  }

  public int frame() {
    return clip == null ? 0 : clip.frameAt(age);
  }

  public boolean complete() {
    return clip != null && !clip.loop && age >= clip.duration - 1e-9;
  }

  public void reset() {
    clip = null;
    requested = "";
    age = previous = 0;
  }

  public void play(String id, long stateKey) {
    if (!id.equals(requested) || stateKey != key) {
      requested = id;
      clip = content.animations.get(id);
      if (clip == null) throw new IllegalArgumentException("Missing animation " + id);
      key = stateKey;
      age = 0;
      previous = -1e-9;
      entered = true;
    }
  }

  public void sample(double progress) {
    if (clip == null) return;
    previous = entered ? -1e-9 : age;
    entered = false;
    age = clip.duration * Math.max(0, Math.min(1, progress));
  }

  public void advance(double dt) {
    if (clip == null || !Double.isFinite(dt) || dt <= 0) return;
    previous = entered ? -1e-9 : age;
    entered = false;
    age += dt;
    int transitions = 0;
    while (!clip.loop && age >= clip.duration && !clip.next.isEmpty() && transitions++ < 1024) {
      age -= clip.duration;
      clip = content.animations.get(clip.next);
      previous = -1e-9;
    }
    if (!clip.loop) age = Math.min(age, clip.duration);
  }

  /**
   * Includes skipped presentation frames, and repeated loop events, without per-frame allocations.
   */
  public boolean event(String name) {
    if (clip == null) return false;
    for (var e : clip.events)
      if (e.name().equals(name)) {
        if (!clip.loop) {
          if (previous < e.at() && age + 1e-9 >= e.at()) return true;
        } else if (Math.floor((age - e.at() + 1e-9) / clip.duration)
            > Math.floor((previous - e.at()) / clip.duration)) return true;
      }
    return false;
  }
}
