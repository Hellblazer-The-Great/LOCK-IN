package android.graphics;

import java.util.ArrayDeque;

/** Instrumented draw commands/transforms only. This does not claim Android pixels were rendered. */
public final class Canvas {
  public long draws;
  public double lastX, lastY, lastScaleX, lastScaleY;
  public boolean filtered;
  public float targetLeft, targetTop, targetRight, targetBottom;
  private double x, y, sx = 1, sy = 1;
  private final ArrayDeque<double[]> saved = new ArrayDeque<>();

  public Canvas() {}

  public Canvas(Bitmap target) {}

  public void clipRect(float l,float t,float r,float b){}
  public void save() {
    saved.push(new double[] {x, y, sx, sy});
  }

  public void restore() {
    var s = saved.pop();
    x = s[0];
    y = s[1];
    sx = s[2];
    sy = s[3];
  }

  public void translate(float dx, float dy) {
    x += dx * sx;
    y += dy * sy;
  }

  public void scale(float dx, float dy) {
    sx *= dx;
    sy *= dy;
  }

  public void drawBitmap(Bitmap bitmap, Rect source, RectF target, Paint paint) {
    targetLeft = target.left;
    targetTop = target.top;
    targetRight = target.right;
    targetBottom = target.bottom;
    record(paint);
  }

  public void drawBitmap(Bitmap bitmap, float x, float y, Paint paint) {
    record(paint);
  }

  public void drawRect(float l, float t, float r, float b, Paint paint) {
    record(paint);
  }

  private void record(Paint p) {
    draws++;
    lastX = x;
    lastY = y;
    lastScaleX = sx;
    lastScaleY = sy;
    filtered = p.filtered;
  }
}
