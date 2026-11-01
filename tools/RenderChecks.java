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
    SpritePainter painter = new SpritePainter(new AssetManager(),true);
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
    Bitmap.maxWidth=Bitmap.maxHeight=0;
    SpritePainter world=new SpritePainter(new AssetManager());
    check(world.worldCacheBytes()==ChunkLayout.BYTES&&world.worldCacheBytes()<16*1024*1024,"bounded section cache memory");
    check(Bitmap.maxWidth<=960&&Bitmap.maxHeight<=540,"no full-world bitmap allocation");
    decodes=BitmapFactory.decodes;allocations=Bitmap.created;
    int most=0;
    for(float cy:new float[]{0,100,400,540})for(float cx:new float[]{0,200,700,1100,1700,1920}){
        long start=c.draws;world.worldBackground(c,cx,cy);world.worldTerrain(c,cx,cy);
        int commands=(int)(c.draws-start);most=Math.max(most,commands);
        check(commands>=7&&commands<=11,"only visible scenery chunks drawn");
    }
    check(BitmapFactory.decodes==decodes&&Bitmap.created==allocations,"continuous scrolling creates/decodes no bitmaps");
    System.out.println("Continuous world background/scenery maximum draw commands: "+most+", cache bytes: "+world.worldCacheBytes());
    System.out.println("Passed " + checks + " renderer command/cache/anchor checks");
  }
}
