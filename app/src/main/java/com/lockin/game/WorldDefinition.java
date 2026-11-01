package com.lockin.game;

/**
 * One authored world. Logical viewport remains 960x540; collision never uses camera coordinates.
 */
public final class WorldDefinition {
  public static final float WIDTH = 2880, HEIGHT = 1080, GROUND = 940;
  public static final float LADDER_WIDTH = 80, OPENING_WIDTH = 112, ENTRY_RADIUS = 30;
  public static final float HP_X = 300, HP_FEET = 284, POWER_X = 1530, POWER_FEET = 220;
  public static final String[] REGIONS = {"Training Grounds", "Upper Courtyard", "Castle Approach"};
  public static final AreaDefinition WORLD = create();

  private static AreaDefinition.Platform floor(float l, float r, float y) {
    return new AreaDefinition.Platform(l, r, y, false);
  }

  private static AreaDefinition create() {
    var decks =
        new AreaDefinition.Platform[] {
          floor(0, 2880, 940),
          floor(256, 640, 812),
          floor(320, 704, 668),
          floor(192, 576, 476),
          floor(224, 608, 284),
          floor(1056, 1536, 796),
          floor(1248, 1696, 604),
          floor(1088, 1600, 412),
          floor(1120, 1632, 220),
          floor(2112, 2560, 812),
          floor(2304, 2784, 620),
          floor(2112, 2688, 428),
          floor(2176, 2720, 236),
          floor(640, 1152, 668),
          floor(1664, 2272, 604),
          floor(1568, 2176, 428)
        };
    var ladders =
        new AreaDefinition.Ladder[] {
          new AreaDefinition.Ladder(440, 0, 1), new AreaDefinition.Ladder(560, 1, 2),
          new AreaDefinition.Ladder(384, 2, 3), new AreaDefinition.Ladder(480, 3, 4),
          new AreaDefinition.Ladder(1160, 0, 5), new AreaDefinition.Ladder(1392, 5, 6),
          new AreaDefinition.Ladder(1488, 6, 7), new AreaDefinition.Ladder(1280, 7, 8),
          new AreaDefinition.Ladder(2240, 0, 9), new AreaDefinition.Ladder(2448, 9, 10),
          new AreaDefinition.Ladder(2576, 10, 11), new AreaDefinition.Ladder(2352, 11, 12),
          new AreaDefinition.Ladder(1088, 5, 13), new AreaDefinition.Ladder(2160, 9, 14)
        };
    java.util.ArrayList<AreaDefinition.Link> links = new java.util.ArrayList<>();
    for (var l : ladders) {
      links.add(
          new AreaDefinition.Link(l.bottom(), l.top(), AreaDefinition.Travel.LADDER, l.x(), l.x()));
      links.add(
          new AreaDefinition.Link(l.top(), l.bottom(), AreaDefinition.Travel.LADDER, l.x(), l.x()));
    }
    connect(links, 2, 13, AreaDefinition.Travel.WALK, 672, 736);
    connect(links, 6, 14, AreaDefinition.Travel.WALK, 1680, 1740);
    connect(links, 11, 15, AreaDefinition.Travel.WALK, 2140, 2080);
    connect(links, 7, 15, AreaDefinition.Travel.JUMP, 1570, 1660);
    return new AreaDefinition(
        "Shinobi Castle",
        decks,
        ladders,
        new AreaDefinition.Spawn[] {
          new AreaDefinition.Spawn(670, 0),
          new AreaDefinition.Spawn(1450, 5),
          new AreaDefinition.Spawn(2040, 0),
          new AreaDefinition.Spawn(2460, 9),
          new AreaDefinition.Spawn(2660, 10)
        },
        links.toArray(new AreaDefinition.Link[0]),
        WIDTH,
        HEIGHT,
        GROUND,
        true);
  }

  private static void connect(
      java.util.List<AreaDefinition.Link> links,
      int a,
      int b,
      AreaDefinition.Travel type,
      float x,
      float y) {
    links.add(new AreaDefinition.Link(a, b, type, x, y));
    links.add(new AreaDefinition.Link(b, a, type, y, x));
  }

  private WorldDefinition() {}
}
