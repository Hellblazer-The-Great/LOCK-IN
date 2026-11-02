package com.lockin.game;

import android.content.res.AssetManager;
import android.graphics.*;
import com.lockin.game.content.*;
import java.io.*;
import java.nio.file.*;

/** Host draw-command checks. These do not substitute for Android device/raster testing. */
public final class ContentRenderChecks {
  static int checks;

  static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  static void near(double a, double b, String message) {
    check(Math.abs(a - b) < .001, message + " " + a + " != " + b);
  }

  public static void main(String[] args) throws Exception {
    Content content = Content.load(ValidateContent.files(Paths.get("app/src/main/assets")));
    AssetManager assets = new AssetManager();
    ContentPainter painter = new ContentPainter(assets, content);
    painter.setLevel(content.levels.get("castle"));
    check(painter.worldCacheBytes() == 12441600, "bounded 12-chunk pool");
    int created = Bitmap.created, decoded = BitmapFactory.decodes;
    Canvas canvas = new Canvas();
    for (int lap = 0; lap < 3; lap++)
      for (int y = 0; y <= 548; y += 137)
        for (int x = 0; x <= 1920; x += 240) {
          long before = canvas.draws;
          painter.worldTerrain(canvas, x, y);
          check(canvas.draws - before <= 6, "visible chunk draw cap");
        }
    check(Bitmap.created == created, "cache eviction reuses bitmaps");
    check(BitmapFactory.decodes == decoded, "panning never decodes images");
    ContentAnimationClock clock = new ContentAnimationClock(content);
    clock.play("ninja_idle", 1);
    clock.advance(.2);
    painter.character(canvas, clock, 200, 940, true, 255);
    near(canvas.lastScaleX, 3.4, "player configured scale");
    near(canvas.lastScaleY, 3.4, "player aspect ratio");
    near(canvas.lastX, 200, "sprite center anchor");
    near(canvas.lastY, 940, "sprite feet anchor");
    check(!canvas.filtered, "pixel art filtering disabled");
    painter.character(canvas, clock, 200, 940, false, 255);
    near(canvas.lastScaleX, -3.4, "facing mirrors source orientation");
    clock.play("samurai_strike", 2);
    clock.sample(.5);
    painter.character(canvas, clock, 400, 940, false, 255);
    near(canvas.lastScaleX, 8.2, "samurai native left orientation");
    clock.play("samurai_return_strike", 3);
    painter.character(canvas, clock, 400, 940, false, 255);
    near(canvas.lastScaleX, -8.2, "second strike uses return facing");
    for (int i = 0; i < 100; i++) {
      clock.advance(1.0 / 60);
      painter.character(canvas, clock, 400, 940, true, 255);
    }
    check(
        BitmapFactory.decodes == decoded && Bitmap.created == created,
        "animation creates/decodes no per-frame images");
    // Explicit placeholder clips remain a functional renderer mode.
    String player = Files.readString(Paths.get("app/src/main/assets/animations/player.json"));
    player =
        player.replace("\"id\": \"ninja_idle\"", "\"id\": \"ninja_idle\", \"placeholder\": true");
    final String replacement = player;
    ContentSource disk = ValidateContent.files(Paths.get("app/src/main/assets"));
    Content placeholders =
        Content.load(
            path ->
                path.equals("animations/player.json")
                    ? new ByteArrayInputStream(
                        replacement.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    : disk.open(path));
    ContentPainter fallback = new ContentPainter(assets, placeholders);
    clock = new ContentAnimationClock(placeholders);
    clock.play("ninja_idle", 1);
    long count = canvas.draws;
    fallback.character(canvas, clock, 200, 940, true, 255);
    check(canvas.draws == count + 1, "explicit placeholder draws a body rectangle");
    System.out.println("Passed " + checks + " content renderer/cache/orientation checks");
  }
}
