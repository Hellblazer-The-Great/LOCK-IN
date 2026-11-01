package com.lockin.game;

/** Immutable authored geometry. Surface zero is the solid arena floor; others are one-way decks. */
public final class AreaDefinition {
  public record Platform(float left, float right, float top, boolean oneWay) {}

  public record Ladder(float x, int bottom, int top) {}

  public record Spawn(float x, int surface) {}

  public enum Travel {
    LADDER,
    JUMP,
    DROP,
    WALK
  }

  public record Link(int from, int to, Travel travel, float launch, float destination) {}

  public record Solid(float left, float right, float top, float bottom) {}

  public final float width, height, groundY;
  public final boolean continuous;
  private final Solid[] solids;

  public int solidCount() {
    return solids.length;
  }

  public Solid solid(int i) {
    return solids[i];
  }

  public final String name;
  private final Platform[] platforms;
  private final Ladder[] ladders;
  private final Spawn[] spawns;
  private final Link[] links;

  public AreaDefinition(
      String name, Platform[] platforms, Ladder[] ladders, Spawn[] spawns, Link[] links) {
    this(name, platforms, ladders, spawns, links, 960, 540, 380, false);
  }

  public AreaDefinition(
      String name,
      Platform[] platforms,
      Ladder[] ladders,
      Spawn[] spawns,
      Link[] links,
      float width,
      float height,
      float groundY,
      boolean continuous) {
    this.width = width;
    this.height = height;
    this.groundY = groundY;
    this.continuous = continuous;
    this.name = name;
    this.platforms = platforms.clone();
    this.ladders = ladders.clone();
    this.spawns = spawns.clone();
    this.links = links.clone();
    java.util.ArrayList<Solid> pieces = new java.util.ArrayList<>();
    for (int i = 0; i < platforms.length; i++) {
      Platform deck = platforms[i];
      java.util.ArrayList<Float> holes = new java.util.ArrayList<>();
      if (continuous) for (Ladder l : ladders) if (l.top() == i) holes.add(l.x());
      java.util.Collections.sort(holes);
      float cursor = deck.left();
      for (float x : holes) {
        float edge = x - WorldDefinition.OPENING_WIDTH / 2;
        if (edge > cursor)
          pieces.add(new Solid(cursor, edge, deck.top(), i == 0 ? height : deck.top() + 32));
        cursor = x + WorldDefinition.OPENING_WIDTH / 2;
      }
      if (cursor < deck.right())
        pieces.add(new Solid(cursor, deck.right(), deck.top(), i == 0 ? height : deck.top() + 32));
    }
    solids = pieces.toArray(new Solid[0]);
  }

  public int platformCount() {
    return platforms.length;
  }

  public Platform platform(int i) {
    return platforms[i];
  }

  public int ladderCount() {
    return ladders.length;
  }

  public Ladder ladder(int i) {
    return ladders[i];
  }

  public int spawnCount() {
    return spawns.length;
  }

  public Spawn spawn(int i) {
    return spawns[i];
  }

  public int linkCount() {
    return links.length;
  }

  public Link link(int i) {
    return links[i];
  }

  public int surface(float center, float feet) {
    int best = 0;
    float distance = Float.MAX_VALUE;
    for (int i = 0; i < platforms.length; i++) {
      Platform s = platforms[i];
      if (center >= s.left && center <= s.right && Math.abs(feet - s.top) < distance) {
        best = i;
        distance = Math.abs(feet - s.top);
      }
    }
    return best;
  }

  private static Platform deck(float l, float r, float y) {
    return new Platform(l, r, y, true);
  }

  private static Link climb(int from, int to, float x) {
    return new Link(from, to, Travel.LADDER, x, x);
  }

  public static final AreaDefinition[] AREAS = {
    new AreaDefinition(
        "Training Grounds",
        new Platform[] {new Platform(0, 960, 380, false), deck(320, 576, 268)},
        new Ladder[] {new Ladder(448, 0, 1)},
        new Spawn[] {new Spawn(670, 0)},
        new Link[] {
          climb(0, 1, 448),
          climb(1, 0, 448),
          new Link(0, 1, Travel.JUMP, 280, 350),
          new Link(1, 0, Travel.DROP, 300, 280)
        }),
    new AreaDefinition(
        "Upper Courtyard",
        new Platform[] {new Platform(0, 960, 380, false), deck(192, 416, 284), deck(448, 704, 188)},
        new Ladder[] {new Ladder(288, 0, 1), new Ladder(544, 0, 2)},
        new Spawn[] {new Spawn(594, 2)},
        new Link[] {
          climb(0, 1, 288),
          climb(1, 0, 288),
          climb(0, 2, 544),
          climb(2, 0, 544),
          new Link(1, 2, Travel.JUMP, 390, 480),
          new Link(2, 1, Travel.JUMP, 470, 390),
          new Link(1, 0, Travel.DROP, 172, 150),
          new Link(2, 0, Travel.DROP, 728, 750)
        }),
    new AreaDefinition(
        "Castle Approach",
        new Platform[] {new Platform(0, 960, 380, false), deck(224, 480, 268), deck(544, 800, 172)},
        new Ladder[] {new Ladder(352, 0, 1), new Ladder(672, 0, 2)},
        new Spawn[] {new Spawn(400, 0), new Spawn(260, 1), new Spawn(718, 2)},
        new Link[] {
          climb(0, 1, 352),
          climb(1, 0, 352),
          climb(0, 2, 672),
          climb(2, 0, 672),
          new Link(1, 2, Travel.JUMP, 452, 574),
          new Link(2, 1, Travel.JUMP, 570, 450),
          new Link(1, 0, Travel.DROP, 200, 180),
          new Link(2, 0, Travel.DROP, 824, 846)
        })
  };
}
