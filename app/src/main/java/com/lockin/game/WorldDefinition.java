package com.lockin.game;

/**
 * Historical regression aliases. Production loads world geometry and placement from Tiled content.
 */
public final class WorldDefinition {
  public static final float WIDTH = 2880, HEIGHT = 1088, GROUND = 940;
  public static final float LADDER_WIDTH = 80, OPENING_WIDTH = 112, ENTRY_RADIUS = 30;
  public static final float HP_X = 300, HP_FEET = 284, POWER_X = 1530, POWER_FEET = 220;
  public static final String[] REGIONS = {"Training Grounds", "Upper Courtyard", "Castle Approach"};

  /** Regression fixture uses the same external castle data as the APK. */
  public static final AreaDefinition WORLD = FixtureContent.get().levels.get("castle").area;

  private WorldDefinition() {}
}
