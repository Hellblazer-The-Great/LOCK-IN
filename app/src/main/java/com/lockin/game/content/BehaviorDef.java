package com.lockin.game.content;

import java.util.*;

/** Validated finite state machine settings. No executable scripts. */
public final class BehaviorDef {
  public final String id, selection;
  public final List<String> attacks;
  public final List<Double> weights;
  public final float detection, engagement;
  public final double betweenMoves, staggerRecovery;
  public final boolean chase, facePlayer;
  public final long seed;

  public BehaviorDef(
      String id,
      String selection,
      List<String> attacks,
      List<Double> weights,
      float detection,
      float engagement,
      double between,
      double stagger,
      boolean chase,
      boolean face,
      long seed) {
    this.id = id;
    this.selection = selection;
    this.attacks = Collections.unmodifiableList(new ArrayList<>(attacks));
    this.weights = Collections.unmodifiableList(new ArrayList<>(weights));
    this.detection = detection;
    this.engagement = engagement;
    betweenMoves = between;
    staggerRecovery = stagger;
    this.chase = chase;
    facePlayer = face;
    this.seed = seed;
  }
}
