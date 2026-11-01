package com.lockin.game;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Eagerly loads a small explicit asset manifest. No decoding, cropping, or allocation in draw. */
public final class SpritePainter {
  private final EnumMap<SpriteClip, Bitmap> sprites = new EnumMap<>(SpriteClip.class);
  private final Map<String, Bitmap> cache = new HashMap<>();
  private final Bitmap[] backgrounds = new Bitmap[5];
  private final Bitmap[] areaLayers = new Bitmap[3];
  private final Bitmap[] chunks = new Bitmap[ChunkLayout.COUNT];
  private final Bitmap tiles, terrain;
  private final Paint paint = new Paint();
  private final Rect source = new Rect();
  private final RectF target = new RectF();
  private final BitmapFactory.Options options = new BitmapFactory.Options();

  public SpritePainter(AssetManager assets) throws IOException {
    this(assets, false);
  }

  public SpritePainter(AssetManager assets, boolean legacyScreens) throws IOException {
    paint.setFilterBitmap(false);
    paint.setAntiAlias(false);
    paint.setDither(false);
    options.inScaled = false;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try {
      for (SpriteClip clip : SpriteClip.values()) sprites.put(clip, load(assets, clip));
      for (int i = 0; i < backgrounds.length; i++)
        backgrounds[i] =
            bitmap(assets, "sprites/Samurai_Bundle/Map/Zen_Island/Background_" + (i + 1) + ".png");
      terrain = bitmap(assets, TerrainArt.SHEET);
      if (terrain.getWidth() != 512 || terrain.getHeight() != 96)
        throw new IOException("Terrain sheet layout mismatch");
      tiles = bitmap(assets, "sprites/Samurai_Bundle/Map/Zen_Island/Tileset_1.png");
      // Geometry is immutable. Composite hundreds of static tile draws once, not every frame.
      if (legacyScreens)
        for (int i = 0; i < areaLayers.length; i++) {
          Bitmap layer =
              Bitmap.createBitmap(
                  (int) GameWorld.WIDTH, (int) GameWorld.HEIGHT, Bitmap.Config.ARGB_8888);
          layer.setDensity(Bitmap.DENSITY_NONE);
          areaLayers[i] = layer;
          cache.put("area-layer-" + i, layer);
          Canvas canvas = new Canvas(layer);
          legacyGround(canvas);
          drawTerrain(canvas, AreaDefinition.AREAS[i], i);
        }
      else
        for (int row = 0; row < ChunkLayout.ROWS; row++)
          for (int col = 0; col < ChunkLayout.COLUMNS; col++) {
            int index = row * ChunkLayout.COLUMNS + col;
            Bitmap chunk =
                Bitmap.createBitmap(ChunkLayout.WIDTH, ChunkLayout.HEIGHT, Bitmap.Config.ARGB_8888);
            chunk.setDensity(Bitmap.DENSITY_NONE);
            chunks[index] = chunk;
            cache.put("world-chunk-" + index, chunk);
            Canvas canvas = new Canvas(chunk);
            canvas.translate(-col * ChunkLayout.WIDTH, -row * ChunkLayout.HEIGHT);
            worldScenery(canvas);
          }
    } catch (IOException | RuntimeException error) {
      for (Bitmap bitmap : cache.values()) bitmap.recycle();
      throw new IOException("Cannot load sprite manifest: " + error.getMessage(), error);
    }
  }

  private Bitmap decode(AssetManager assets, String path) throws IOException {
    try (InputStream stream = assets.open(path)) {
      Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
      if (bitmap == null) throw new IOException("Invalid image: " + path);
      bitmap.setDensity(Bitmap.DENSITY_NONE);
      return bitmap;
    }
  }

  private Bitmap bitmap(AssetManager assets, String path) throws IOException {
    Bitmap result = cache.get(path);
    if (result == null) {
      result = decode(assets, path);
      cache.put(path, result);
    }
    return result;
  }

  private Bitmap load(AssetManager assets, SpriteClip clip) throws IOException {
    if (!clip.sequence()) {
      Bitmap sheet = bitmap(assets, clip.asset);
      if (sheet.getWidth() != clip.columns * clip.frameWidth
          || sheet.getHeight() % clip.frameHeight != 0
          || sheet.getHeight() / clip.frameHeight * clip.columns < clip.frames)
        throw new IOException("Sheet layout mismatch: " + clip.asset);
      return sheet;
    }
    Bitmap atlas =
        Bitmap.createBitmap(
            clip.frameWidth * clip.columns, clip.frameHeight, Bitmap.Config.ARGB_8888);
    atlas.setDensity(Bitmap.DENSITY_NONE);
    cache.put(clip.asset, atlas);
    Canvas canvas = new Canvas(atlas);
    for (int i = 0; i < clip.frames; i++) {
      String path = String.format(Locale.ROOT, clip.asset, i + 1);
      Bitmap frame = decode(assets, path);
      try {
        if (frame.getWidth() != clip.frameWidth || frame.getHeight() != clip.frameHeight)
          throw new IOException("Frame layout mismatch: " + path);
        canvas.drawBitmap(frame, i * clip.frameWidth, 0, paint);
      } finally {
        frame.recycle();
      }
    }
    return atlas;
  }

  public void character(
      Canvas canvas,
      AnimationClock animation,
      float centerX,
      float feetY,
      boolean facingRight,
      int alpha) {
    SpriteClip clip = animation.clip();
    if (clip == null) return;
    int x = clip.sourceX(animation.frame()), y = clip.sourceY(animation.frame());
    source.set(x, y, x + clip.frameWidth, y + clip.frameHeight);
    target.set(
        -clip.anchorX,
        -clip.anchorY,
        clip.frameWidth - clip.anchorX,
        clip.frameHeight - clip.anchorY);
    paint.setAlpha(alpha);
    canvas.save();
    canvas.translate(centerX, feetY);
    canvas.scale(clip.flip(facingRight) ? -clip.scale : clip.scale, clip.scale);
    canvas.drawBitmap(sprites.get(clip), source, target, paint);
    canvas.restore();
    paint.setAlpha(255);
  }

  private void image(Canvas canvas, Bitmap bitmap, float l, float t, float r, float b) {
    target.set(l, t, r, b);
    canvas.drawBitmap(bitmap, null, target, paint);
  }

  private void tile(Canvas c, TerrainArt tile, float x, float y, float w, float h) {
    source.set(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height);
    target.set(x, y, x + w, y + h);
    c.drawBitmap(terrain, source, target, paint);
  }

  public void area(Canvas c, AreaDefinition area, int index, float playerCenter) {
    backdrop(c, playerCenter);
    image(c, areaLayers[index], 0, 0, GameWorld.WIDTH, GameWorld.HEIGHT);
  }

  private void drawTerrain(Canvas c, AreaDefinition area, int index) {
    if (index == 0)
      for (int y = 380; y < 540; y += 32)
        for (int x = 0; x < 960; x += 32)
          tile(c, y == 380 ? TerrainArt.GRASS : TerrainArt.DIRT, x, y, 32, 32);
    if (index > 0) {
      paint.setColor(index == 1 ? 0x88383B50 : 0xDD242936);
      c.drawRect(0, 0, 960, 380, paint);
      for (int y = 124; y < 380; y += 32)
        for (int x = 0; x < 960; x += 32)
          if (index == 2 || x > 610) tile(c, TerrainArt.WALL, x, y, 32, 32);
      tile(c, index == 1 ? TerrainArt.BLUE_BANNER : TerrainArt.RED_BANNER, 64, 140, 48, 48);
      tile(c, TerrainArt.RED_BANNER, 848, 140, 48, 48);
      if (index == 2) {
        tile(c, TerrainArt.TORCH, 96, 316, 64, 64);
        tile(c, TerrainArt.TORCH, 816, 316, 64, 64);
      }
      for (int y = 380; y < 540; y += 32)
        for (int x = 0; x < 960; x += 32)
          tile(c, y == 380 ? TerrainArt.TOP : TerrainArt.FILL, x, y, 32, 32);
    }
    for (int i = 1; i < area.platformCount(); i++) {
      var deck = area.platform(i);
      for (float x = deck.left(); x < deck.right(); x += 32) {
        float width = Math.min(32, deck.right() - x);
        if (index == 0 || (index == 1 && i == 1)) {
          paint.setColor(0xFF90613E);
          c.drawRect(x, deck.top(), x + width, deck.top() + 16, paint);
          tile(c, TerrainArt.LOG, x, deck.top(), width, 32);
        } else
          tile(
              c,
              x == deck.left()
                  ? TerrainArt.PLATFORM_LEFT
                  : x + 32 >= deck.right() ? TerrainArt.PLATFORM_RIGHT : TerrainArt.PLATFORM,
              x,
              deck.top(),
              width,
              32);
      }
    }
    for (int i = 0; i < area.ladderCount(); i++) {
      var l = area.ladder(i);
      float top = area.platform(l.top()).top(), bottom = area.platform(l.bottom()).top();
      for (float y = top; y < bottom; y += 32)
        tile(
            c,
            y == top ? TerrainArt.LADDER_TOP : TerrainArt.LADDER,
            l.x() - 16,
            y,
            32,
            Math.min(32, bottom - y));
    }
    if (index > 0) {
      tile(c, TerrainArt.DOOR, 0, 316, 64, 64);
      tile(c, TerrainArt.EXIT, 8, 280, 32, 32);
    }
    tile(c, TerrainArt.DOOR, 896, 316, 64, 64);
    tile(c, TerrainArt.EXIT, 916, 280, 32, 32);
  }

  private void tilePart(Canvas c, TerrainArt art, float x, float y, float w, float h) {
    source.set(
        art.x,
        art.y,
        art.x + Math.max(1, Math.round(w / 2)),
        art.y + Math.max(1, Math.round(h / 2)));
    target.set(x, y, x + w, y + h);
    c.drawBitmap(terrain, source, target, paint);
  }

  public long worldCacheBytes() {
    return chunks[0] == null ? 0 : ChunkLayout.BYTES;
  }

  public void worldBackground(Canvas c, float cameraX, float cameraY) {
    backdrop(c, cameraX * .25f + GameWorld.WIDTH / 2);
  }

  public void worldTerrain(Canvas c, float cameraX, float cameraY) {
    for (int row = ChunkLayout.firstRow(cameraY); row <= ChunkLayout.lastRow(cameraY); row++)
      for (int col = ChunkLayout.firstColumn(cameraX);
          col <= ChunkLayout.lastColumn(cameraX);
          col++) {
        int index = row * ChunkLayout.COLUMNS + col;
        image(
            c,
            chunks[index],
            col * ChunkLayout.WIDTH,
            row * ChunkLayout.HEIGHT,
            (col + 1) * ChunkLayout.WIDTH,
            (row + 1) * ChunkLayout.HEIGHT);
      }
  }

  private void worldScenery(Canvas c) {
    var a = WorldDefinition.WORLD;
    source.set(48, 4, 64, 24);
    target.set(64, 860, 128, 940);
    c.drawBitmap(tiles, source, target, paint);
    target.set(832, 860, 896, 940);
    c.drawBitmap(tiles, source, target, paint);
    // Low-contrast structures justify the suspended walkways and tower floors.
    paint.setColor(0x6643362E);
    c.drawRect(1056, 412, 1696, 940, paint);
    paint.setColor(0xBB242936);
    c.drawRect(2016, 172, 2816, 940, paint);
    for (int y = 252; y < 940; y += 32)
      for (int x = 2112; x < 2784; x += 32) tile(c, TerrainArt.WALL, x, y, 32, 32);
    for (int region = 0; region < 2; region++)
      for (int x = region * 960 + 256; x < region * 960 + 704; x += 128) {
        paint.setColor(0xFF68503E);
        c.drawRect(x, region == 0 ? 284 : 412, x + 10, 940, paint);
        for (int y = region == 0 ? 300 : 428; y < 940; y += 128)
          c.drawRect(x, y, x + 96, y + 6, paint);
      }
    tile(c, TerrainArt.BLUE_BANNER, 1088, 456, 64, 64);
    tile(c, TerrainArt.RED_BANNER, 2640, 472, 64, 64);
    for (int i = 0; i < a.solidCount(); i++) {
      var solid = a.solid(i);
      for (float y = solid.top(); y < solid.bottom(); y += 32)
        for (float x = solid.left(); x < solid.right(); x += 32) {
          float w = Math.min(32, solid.right() - x), h = Math.min(32, solid.bottom() - y);
          int region = Math.min(2, (int) (x / 960));
          if (solid.top() == WorldDefinition.GROUND)
            tilePart(
                c,
                region == 0
                    ? (y == solid.top() ? TerrainArt.GRASS : TerrainArt.DIRT)
                    : (y == solid.top() ? TerrainArt.TOP : TerrainArt.FILL),
                x,
                y,
                w,
                h);
          else if (region == 0 || i % 3 == 1) {
            paint.setColor(0xFF90613E);
            c.drawRect(x, y, x + w, y + h, paint);
            tilePart(c, TerrainArt.LOG, x, y, w, h);
          } else tilePart(c, y == solid.top() ? TerrainArt.TOP : TerrainArt.FILL, x, y, w, h);
        }
    }
    for (int i = 0; i < a.ladderCount(); i++) {
      var l = a.ladder(i);
      float top = a.platform(l.top()).top(), bottom = a.platform(l.bottom()).top();
      c.save();
      c.clipRect(
          l.x() - WorldDefinition.LADDER_WIDTH / 2,
          top,
          l.x() + WorldDefinition.LADDER_WIDTH / 2,
          bottom);
      for (float y = top; y < bottom; y += WorldDefinition.LADDER_WIDTH)
        tile(
            c,
            y == top ? TerrainArt.LADDER_TOP : TerrainArt.LADDER,
            l.x() - WorldDefinition.LADDER_WIDTH / 2,
            y,
            WorldDefinition.LADDER_WIDTH,
            WorldDefinition.LADDER_WIDTH);
      c.restore();
    }
    tile(c, TerrainArt.TORCH, 2112, 876, 64, 64);
    tile(c, TerrainArt.TORCH, 2768, 876, 64, 64);
    tile(c, TerrainArt.DOOR, 2816, 876, 64, 64);
    tile(c, TerrainArt.EXIT, 2832, 836, 32, 32);
  }

  private void backdrop(Canvas canvas, float playerCenter) {
    image(canvas, backgrounds[0], 0, 0, 960, 540);
    // Reposition the pack's existing sun into the open sky, retaining its square aspect ratio.
    source.set(78, 45, 118, 85);
    target.set(750, 55, 850, 155);
    canvas.drawBitmap(backgrounds[3], source, target, paint);
    float offset = (playerCenter - GameWorld.WIDTH / 2) / GameWorld.WIDTH;
    image(canvas, backgrounds[1], -24 - offset * 8, -40, 984 - offset * 8, 527);
    image(canvas, backgrounds[2], -24 - offset * 16, -40, 984 - offset * 16, 527);
    image(canvas, backgrounds[4], -24 - offset * 24, -40, 984 - offset * 24, 527);
  }

  private void legacyGround(Canvas canvas) {
    // One top/dirt tile repeated across the existing flat platform; not a tilemap system.
    source.set(8, 0, 16, 24);
    for (int x = 0; x < 960; x += 40) {
      target.set(x, GameWorld.GROUND_Y, x + 40, GameWorld.GROUND_Y + 120);
      canvas.drawBitmap(tiles, source, target, paint);
    }
    paint.setColor(0xFF553C36);
    canvas.drawRect(0, 500, 960, 540, paint);
    source.set(48, 4, 64, 24);
    target.set(65, 300, 129, 380);
    canvas.drawBitmap(tiles, source, target, paint);
    target.set(850, 300, 914, 380);
    canvas.drawBitmap(tiles, source, target, paint);
  }
}
