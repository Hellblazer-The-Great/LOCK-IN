package com.lockin.game;

import android.content.res.AssetManager;
import android.graphics.*;

/** Actual SpritePainter draw commands against JVM instrumentation, not a device/GPU benchmark. */
public final class RenderChecks {
  static int checks;

  static void check(boolean ok, String label) {
    checks++;
    if (!ok) throw new AssertionError(label);
  }

  public static void main(String[] args) throws Exception {
    SpritePainter painter = new SpritePainter(new AssetManager());
    int decodes = BitmapFactory.decodes, allocations = Bitmap.created;
    Canvas c = new Canvas();
    for (int i = 0; i < 3; i++) {
      long start = c.draws;
      painter.area(c, AreaDefinition.AREAS[i], i, 480);
      System.out.println(
          "Area " + i + " terrain/background draw commands per frame: " + (c.draws - start));
      if (args.length == 0)
        check(
            c.draws - start == 6, "five parallax background draws plus one cached static terrain");
    }
    for (int frame = 0; frame < 300; frame++)
      painter.area(c, AreaDefinition.AREAS[frame % 3], frame % 3, frame * 2);
    check(BitmapFactory.decodes == decodes, "no frame/transition PNG decoding");
    check(Bitmap.created == allocations, "no frame/transition bitmap allocations");
    AnimationClock clock = new AnimationClock();
    for (SpriteClip clip : SpriteClip.values())
      for (boolean face : new boolean[] {true, false}) {
        clock.play(clip, 0, 0, 12, false, 0);
        painter.character(c, clock, 400, 268, face, 255);
        check(
            Math.abs(c.lastX + (c.targetLeft + clip.anchorX) * c.lastScaleX - 400) < .00001
                && Math.abs(c.lastY + (c.targetTop + clip.anchorY) * c.lastScaleY - 268) < .00001,
            "source feet anchor maps to world feet " + clip);
        check(
            Math.abs(c.lastScaleX) == clip.scale && c.lastScaleY == clip.scale,
            "uniform pixel scale " + clip);
        check((c.lastScaleX < 0) == clip.flip(face), "actual Canvas flip " + clip);
        check(!c.filtered, "nearest-neighbor rendering " + clip);
      }
    System.out.println("Passed " + checks + " renderer command/cache/anchor checks");
  }
}
