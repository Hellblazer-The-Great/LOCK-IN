package com.lockin.game;

/** Render-only dead-zone follow with exponential smoothing and exact world bounds. */
public final class Camera {
  public static final float DEAD_X = 120, DEAD_Y = 65;
  private float x, y;

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public void reset(Player p, AreaDefinition world) {
    x = clamp(p.x + p.width / 2 - GameWorld.WIDTH / 2, world.width - GameWorld.WIDTH);
    y = clamp(p.y + p.height / 2 - GameWorld.HEIGHT / 2, world.height - GameWorld.HEIGHT);
  }

  public void update(Player p, AreaDefinition world, double dt) {
    if (!Double.isFinite(dt) || dt <= 0) return;
    float cx = p.x + p.width / 2, cy = p.y + p.height / 2;
    float dx = cx - (x + GameWorld.WIDTH / 2), dy = cy - (y + GameWorld.HEIGHT / 2);
    float tx = x + (dx > DEAD_X ? dx - DEAD_X : dx < -DEAD_X ? dx + DEAD_X : 0);
    float ty = y + (dy > DEAD_Y ? dy - DEAD_Y : dy < -DEAD_Y ? dy + DEAD_Y : 0);
    float alpha = (float) (1 - Math.exp(-10 * dt));
    x = clamp(x + (tx - x) * alpha, world.width - GameWorld.WIDTH);
    y = clamp(y + (ty - y) * alpha, world.height - GameWorld.HEIGHT);
  }

  private static float clamp(float value, float maximum) {
    return Math.max(0, Math.min(Math.max(0, maximum), value));
  }

  public boolean visible(float l, float t, float r, float b) {
    return r > x && l < x + GameWorld.WIDTH && b > y && t < y + GameWorld.HEIGHT;
  }
}
