package com.lockin.game;

import android.content.res.AssetManager;
import android.graphics.*;
import com.lockin.game.content.*;
import java.io.*;
import java.util.*;

/** Data-selected assets decoded once. Twelve reusable terrain chunks bound cache memory. */
public final class ContentPainter {
  private static final int CW = 480, CH = 540, CACHE = 12;
  private final Map<String, Bitmap> images = new HashMap<>();
  private final Paint paint = new Paint();
  private final Rect source = new Rect();
  private final RectF target = new RectF();
  private final Bitmap[] chunks = new Bitmap[CACHE];
  private final Canvas[] canvases = new Canvas[CACHE];
  private final int[] column = new int[CACHE], row = new int[CACHE];
  private final long[] touched = new long[CACHE];
  private long stamp;
  private LevelDef level;

  public ContentPainter(AssetManager assets, Content content) throws IOException {
    paint.setFilterBitmap(false);
    paint.setAntiAlias(false);
    paint.setDither(false);
    Arrays.fill(column, -1);
    try {
      for (AnimationDef a : content.animations.values())
        if (!a.placeholder) for (var f : a.frames) load(assets, f.asset());
      for (LevelDef l : content.levels.values()) {
        if (!l.background.isEmpty()) load(assets, l.background);
        for (var tile : l.tiles.values()) load(assets, tile.asset());
      }
      for (int i = 0; i < CACHE; i++) {
        chunks[i] = Bitmap.createBitmap(CW, CH, Bitmap.Config.ARGB_8888);
        chunks[i].setDensity(Bitmap.DENSITY_NONE);
        canvases[i] = new Canvas(chunks[i]);
      }
    } catch (IOException | RuntimeException e) {
      for (Bitmap b : images.values()) b.recycle();
      for (Bitmap b : chunks) if (b != null) b.recycle();
      throw new IOException("Content rendering: " + e.getMessage(), e);
    }
  }

  private void load(AssetManager assets, String path) throws IOException {
    if (images.containsKey(path)) return;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream stream = assets.open(path)) {
      Bitmap b = BitmapFactory.decodeStream(stream, null, options);
      if (b == null) throw new IOException(path + ": bitmap decoding failed");
      b.setDensity(Bitmap.DENSITY_NONE);
      images.put(path, b);
    }
  }

  public long worldCacheBytes() {
    return (long) CW * CH * CACHE * 4;
  }

  public void setLevel(LevelDef next) {
    if (level != next) {
      level = next;
      Arrays.fill(column, -1);
      Arrays.fill(touched, 0);
    }
  }

  public void worldBackground(Canvas c, float x, float y) {
    paint.setColor(0xff182b3d);
    c.drawRect(0, 0, 960, 540, paint);
    if (!level.background.isEmpty()) {
      target.set(0, 0, 960, 540);
      c.drawBitmap(images.get(level.background), null, target, paint);
    }
  }

  public void worldTerrain(Canvas c, float cameraX, float cameraY) {
    int firstC = Math.max(0, (int) (cameraX / CW)),
        lastC =
            Math.min(
                (int) Math.ceil(level.area.width / CW) - 1, (int) ((cameraX + 960 - .01f) / CW));
    int firstR = Math.max(0, (int) (cameraY / CH)),
        lastR =
            Math.min(
                (int) Math.ceil(level.area.height / CH) - 1, (int) ((cameraY + 540 - .01f) / CH));
    for (int r = firstR; r <= lastR; r++)
      for (int col = firstC; col <= lastC; col++) {
        int slot = -1;
        for (int i = 0; i < CACHE; i++)
          if (column[i] == col && row[i] == r) {
            slot = i;
            break;
          }
        if (slot < 0) {
          slot = 0;
          for (int i = 1; i < CACHE; i++) if (touched[i] < touched[slot]) slot = i;
          column[slot] = col;
          row[slot] = r;
          build(slot, col, r);
        }
        touched[slot] = ++stamp;
        target.set(col * CW, r * CH, (col + 1) * CW, (r + 1) * CH);
        c.drawBitmap(chunks[slot], null, target, paint);
      }
  }

  private void build(int slot, int col, int row) {
    chunks[slot].eraseColor(0);
    Canvas c = canvases[slot];
    float left = col * CW, top = row * CH, right = left + CW, bottom = top + CH;
    c.save();
    c.translate(-left, -top);
    for (var layer : level.layers)
      if (layer.visible()) {
        paint.setAlpha(Math.round(layer.opacity() * 255));
        int x0 = Math.max(0, (int) Math.floor((left - layer.x()) / level.tileWidth)),
            y0 = Math.max(0, (int) Math.floor((top - layer.y()) / level.tileHeight));
        int
            x1 =
                Math.min(
                    layer.width() - 1,
                    (int) Math.floor((right - layer.x() - .01f) / level.tileWidth)),
            y1 =
                Math.min(
                    layer.height() - 1,
                    (int) Math.floor((bottom - layer.y() - .01f) / level.tileHeight));
        for (int y = y0; y <= y1; y++)
          for (int x = x0; x <= x1; x++) {
            long raw = layer.gids().get(y * layer.width() + x);
            int gid = (int) (raw & 0x0fffffffL);
            if (gid != 0)
              tile(
                  c,
                  gid,
                  layer.x() + x * level.tileWidth,
                  layer.y() + y * level.tileHeight,
                  level.tileWidth,
                  level.tileHeight,
                  (raw & 0x80000000L) != 0,
                  (raw & 0x40000000L) != 0);
          }
      }
    paint.setAlpha(255);
    for (var d : level.decorations)
      if (d.visible()
          && d.x() < right
          && d.x() + d.width() > left
          && d.y() < bottom
          && d.y() + d.height() > top) {
        c.save();
        c.clipRect(d.x(), d.y(), d.x() + d.width(), d.y() + d.height());
        if (d.gid() == 0) {
          paint.setColor(0xff785744);
          c.drawRect(d.x(), d.y(), d.x() + d.width(), d.y() + d.height(), paint);
        } else {
          float
              startX =
                  d.x()
                      + Math.max(0, (float) Math.floor((left - d.x()) / d.tileSize()))
                          * d.tileSize(),
              startY =
                  d.y()
                      + Math.max(0, (float) Math.floor((top - d.y()) / d.tileSize()))
                          * d.tileSize();
          for (float y = startY; y < Math.min(bottom, d.y() + d.height()); y += d.tileSize())
            for (float x = startX; x < Math.min(right, d.x() + d.width()); x += d.tileSize())
              tile(c, d.gid(), x, y, d.tileSize(), d.tileSize(), false, false);
        }
        c.restore();
      }
    c.restore();
  }

  private void tile(
      Canvas c, int gid, float x, float y, float w, float h, boolean flipX, boolean flipY) {
    var t = level.tiles.get(gid);
    source.set(t.sx(), t.sy(), t.sx() + t.width(), t.sy() + t.height());
    c.save();
    c.translate(x + (flipX ? w : 0), y + (flipY ? h : 0));
    c.scale(flipX ? -1 : 1, flipY ? -1 : 1);
    target.set(0, 0, w, h);
    c.drawBitmap(images.get(t.asset()), source, target, paint);
    c.restore();
  }

  public boolean visible(
      Camera camera, ContentAnimationClock clock, float center, float feet, boolean right) {
    var a = clock.clip();
    if (a == null) return true;
    var f = a.frames.get(clock.frame());
    float l = -a.anchorX * a.scale, r = (f.width() - a.anchorX) * a.scale;
    if (right != a.facesRight) {
      float old = l;
      l = -r;
      r = -old;
    }
    return camera.visible(
        center + l,
        feet - a.anchorY * a.scale,
        center + r,
        feet + (f.height() - a.anchorY) * a.scale);
  }

  public void character(
      Canvas c, ContentAnimationClock clock, float center, float feet, boolean right, int alpha) {
    var a = clock.clip();
    if (a == null) return;
    var frame = a.frames.get(clock.frame());
    paint.setAlpha(alpha);
    c.save();
    c.translate(center, feet);
    c.scale(right != a.facesRight ? -a.scale : a.scale, a.scale);
    target.set(-a.anchorX, -a.anchorY, frame.width() - a.anchorX, frame.height() - a.anchorY);
    if (a.placeholder) {
      paint.setColor(0xff7ac6d2);
      c.drawRect(target.left, target.top, target.right, target.bottom, paint);
    } else {
      source.set(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height());
      c.drawBitmap(images.get(frame.asset()), source, target, paint);
    }
    c.restore();
    paint.setAlpha(255);
  }
}
