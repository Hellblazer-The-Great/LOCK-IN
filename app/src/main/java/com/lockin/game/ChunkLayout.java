package com.lockin.game;

/** Small immutable cache partitions; at most six chunks overlap a 960x540 viewport. */
public final class ChunkLayout {
  public static final int WIDTH = 480, HEIGHT = 540, COLUMNS = 6, ROWS = 2, COUNT = COLUMNS * ROWS;
  public static final long BYTES = (long) WIDTH * HEIGHT * COUNT * 4;

  public static int firstColumn(float x) {
    return Math.max(0, Math.min(COLUMNS - 1, (int) (x / WIDTH)));
  }

  public static int lastColumn(float x) {
    return firstColumn(x + GameWorld.WIDTH - .01f);
  }

  public static int firstRow(float y) {
    return Math.max(0, Math.min(ROWS - 1, (int) (y / HEIGHT)));
  }

  public static int lastRow(float y) {
    return firstRow(y + GameWorld.HEIGHT - .01f);
  }

  private ChunkLayout() {}
}
