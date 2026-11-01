package com.lockin.game;

/** Only verified spritesheet entries used by this release. Native tile size 16, drawn at 2x. */
public enum TerrainArt {
  GRASS(336, 0, 16, 16),
  DIRT(384, 0, 16, 16),
  LOG(480, 64, 16, 16),
  TORCH(240, 64, 16, 16),
  DOOR(32, 0, 32, 32),
  TOP(112, 0, 16, 16),
  FILL(160, 0, 16, 16),
  PLATFORM_LEFT(240, 0, 16, 16),
  PLATFORM(256, 0, 16, 16),
  PLATFORM_RIGHT(272, 0, 16, 16),
  WALL(480, 32, 16, 16),
  LADDER_TOP(96, 64, 16, 16),
  LADDER(112, 64, 16, 16),
  EXIT(160, 64, 16, 16),
  RED_BANNER(208, 64, 16, 16),
  BLUE_BANNER(224, 64, 16, 16);
  public static final String SHEET =
      "sprites/tilesets/Pixel_Platformer_Tileset_Vol3_v1.0/spritesheet.png";
  public final int x, y, width, height;

  TerrainArt(int x, int y, int w, int h) {
    this.x = x;
    this.y = y;
    this.width = w;
    this.height = h;
  }
}
