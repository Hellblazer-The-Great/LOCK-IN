package com.lockin.game.content;

import java.util.*;

/** A frame may come from an atlas rectangle or an individual existing PNG. */
public final class AnimationDef {
  public record Frame(String asset, int x, int y, int width, int height, double seconds) {}

  public record Event(String name, double at) {}

  public final String id, next;
  public final List<Frame> frames;
  public final List<Event> events;
  public final float anchorX, anchorY, scale;
  public final boolean loop, facesRight, placeholder;
  public final double duration;

  public AnimationDef(
      String id,
      List<Frame> frames,
      List<Event> events,
      float ax,
      float ay,
      float scale,
      boolean loop,
      boolean facing,
      String next,
      boolean placeholder) {
    this.id = id;
    this.frames = Collections.unmodifiableList(new ArrayList<>(frames));
    this.events = Collections.unmodifiableList(new ArrayList<>(events));
    anchorX = ax;
    anchorY = ay;
    this.scale = scale;
    this.loop = loop;
    facesRight = facing;
    this.next = next;
    this.placeholder = placeholder;
    double d = 0;
    for (Frame f : frames) d += f.seconds;
    duration = d;
  }

  public int frameAt(double age) {
    if (loop) age = age % duration;
    double t = 0;
    for (int i = 0; i < frames.size(); i++) {
      t += frames.get(i).seconds;
      if (age < t - 1e-9) return i;
    }
    return frames.size() - 1;
  }
}
