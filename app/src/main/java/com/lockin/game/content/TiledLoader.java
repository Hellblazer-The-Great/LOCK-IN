package com.lockin.game.content;

import com.lockin.game.AreaDefinition;
import java.io.*;
import java.util.*;

/**
 * Deliberately bounded Tiled subset. Reject unsupported geometry rather than change its meaning.
 */
public final class TiledLoader {
  private final ContentSource source;

  public TiledLoader(ContentSource source) {
    this.source = source;
  }

  private Json.Node read(String path) throws IOException {
    return Json.parse(path, source.text(path));
  }

  public static Map<String, Json.Node> props(Json.Node node) {
    Map<String, Json.Node> p = new LinkedHashMap<>();
    if (node.has("properties"))
      for (Json.Node v : node.get("properties").list()) {
        String k = v.str("name");
        if (p.put(k, v.get("value")) != null) throw v.error("duplicate custom property " + k);
      }
    return p;
  }

  private static String prop(Map<String, Json.Node> p, String key, String fallback) {
    return p.containsKey(key) ? p.get(key).string() : fallback;
  }

  private static float number(Map<String, Json.Node> p, String key, float fallback) {
    return p.containsKey(key) ? (float) p.get(key).range(-100000, 100000) : fallback;
  }

  private static boolean flag(Map<String, Json.Node> p, String key, boolean fallback) {
    return p.containsKey(key) ? p.get(key).bool() : fallback;
  }

  private static String required(Map<String, Json.Node> p, String key, Json.Node context) {
    if (!p.containsKey(key)) throw context.error("missing custom property " + key);
    return p.get(key).string();
  }

  private static void properties(Map<String, Json.Node> p, Json.Node context, String... names) {
    Set<String> a = new HashSet<>(Arrays.asList(names));
    for (String k : p.keySet())
      if (!a.contains(k)) throw context.error("unsupported custom property " + k);
  }

  public LevelDef load(String id, String file) throws IOException {
    Json.Node map = read(file);
    if (!map.str("renderorder", "right-down").equals("right-down"))
      throw map.error("renderorder must be right-down");
    if (!map.str("type").equals("map") || !map.str("orientation").equals("orthogonal"))
      throw map.error("only orthogonal maps are supported");
    if (map.bool("infinite", false))
      throw map.error("infinite/chunked maps are unsupported; use a finite map");
    int columns = map.get("width").integer(1, 1024), rows = map.get("height").integer(1, 1024);
    int tw = map.get("tilewidth").integer(1, 512), th = map.get("tileheight").integer(1, 512);
    float width = columns * tw, height = rows * th;
    if (width < 960 || height < 540 || width > 32768 || height > 32768)
      throw map.error("world dimensions must be 960x540 through 32768x32768 pixels");
    Map<String, Json.Node> mp = props(map);
    properties(mp, map, "name", "playerEntity", "startSpawn", "groundSurface", "backgroundImage");
    Map<Integer, LevelDef.Tile> tiles = new LinkedHashMap<>();
    Map<String, Integer> tileIds = new LinkedHashMap<>();
    for (Json.Node ref : map.get("tilesets").list()) {
      int first = ref.get("firstgid").integer(1, 1000000);
      String path = ContentSource.path(file, ref.str("source"));
      if (!path.endsWith(".tsj")) throw ref.error("external JSON .tsj tileset required");
      Json.Node ts = read(path);
      if (ts.has("tileoffset")
          && (ts.get("tileoffset").num("x", 0) != 0 || ts.get("tileoffset").num("y", 0) != 0))
        throw ts.error("nonzero tileset tileoffset is unsupported");
      if (!ts.str("type").equals("tileset")) throw ts.error("expected tileset");
      int fw = ts.get("tilewidth").integer(1, 512),
          fh = ts.get("tileheight").integer(1, 512),
          count = ts.get("tilecount").integer(1, 65536),
          cols = ts.get("columns").integer(1, 65536);
      int margin = ts.has("margin") ? ts.get("margin").integer(0, 4096) : 0,
          spacing = ts.has("spacing") ? ts.get("spacing").integer(0, 4096) : 0;
      if (margin < 0 || spacing < 0) throw ts.error("negative margin/spacing");
      String image = ContentSource.path(path, ts.str("image"));
      try (InputStream ignored = source.open(image)) {
      } catch (IOException e) {
        throw new IOException(path + ".image: " + image + " is missing", e);
      }
      int iw = ts.get("imagewidth").integer(1, 32768), ih = ts.get("imageheight").integer(1, 32768);
      Set<Integer> colliding = new HashSet<>();
      if (ts.has("tiles"))
        for (Json.Node t : ts.get("tiles").list()) {
          if (t.has("animation") || t.has("objectgroup") || t.has("image"))
            throw t.error(
                "animated/collection tiles and tile collision shapes are unsupported; use rectangle"
                    + " map objects or solid=true");
          int ti = t.get("id").integer(0, count - 1);
          Map<String, Json.Node> tp = props(t);
          properties(tp, t, "solid");
          if (flag(tp, "solid", false)) colliding.add(ti);
        }
      for (int i = 0; i < count; i++) {
        String tileId = ts.str("name") + ":" + i;
        if (tileIds.put(tileId, first + i) != null)
          throw ts.error("duplicate tileset tile ID " + tileId);
        int x = margin + (i % cols) * (fw + spacing), y = margin + (i / cols) * (fh + spacing);
        if (x + fw > iw || y + fh > ih) throw ts.error("tile " + i + " exceeds image dimensions");
        if (tiles.put(
                first + i, new LevelDef.Tile(first + i, image, x, y, fw, fh, colliding.contains(i)))
            != null) throw ref.error("overlapping tileset GIDs");
      }
    }
    List<LevelDef.Layer> layers = new ArrayList<>();
    List<Json.Node> objects = new ArrayList<>();
    List<float[]> objectOffsets = new ArrayList<>();
    layers(map.get("layers"), 0, 0, true, 1, layers, objects, objectOffsets, tiles);
    for (var layer : layers)
      for (long raw : layer.gids()) {
        var tile = tiles.get((int) (raw & 0x0fffffffL));
        if (tile != null && (tile.width() != tw || tile.height() != th))
          throw map.error(
              "tile layer images must match map tilewidth/tileheight; use decoration objects for"
                  + " scaled art");
      }
    List<LevelDef.Decoration> decorations = new ArrayList<>();
    List<Integer> platformArt = new ArrayList<>();
    List<Boolean> platformVisible = new ArrayList<>();
    List<AreaDefinition.Platform> platforms = new ArrayList<>();
    List<AreaDefinition.Solid> solids = new ArrayList<>();
    Map<String, Integer> surfaces = new LinkedHashMap<>();
    Map<String, Json.Node> named = new LinkedHashMap<>();
    // Named rectangles are navigable surfaces, in editor object order; stable references use names.
    for (int i = 0; i < objects.size(); i++) {
      Json.Node o = objects.get(i);
      String type = type(o);
      String name = o.str("name", "");
      if (!name.isEmpty() && named.put(name, o) != null)
        throw o.error("duplicate object name " + name);
      if (type.equals("platform") || type.equals("collision")) {
        if (name.isEmpty()) throw o.error("collision/platform requires a stable name");
        float[] off = objectOffsets.get(i);
        float x = (float) o.num("x", 0) + off[0],
            y = (float) o.num("y", 0) + off[1],
            w = o.f("width", 1, width),
            h = o.f("height", 1, height);
        bounds(o, x, y, w, h, width, height);
        Map<String, Json.Node> p = props(o);
        properties(p, o, "oneWay", "tileGid", "tileId");
        if (flag(p, "oneWay", false))
          throw o.error("oneWay=true is unsupported for solid castle floors");
        int art = tileRef(p, tileIds, o);
        if (art != 0 && !tiles.containsKey(art)) throw o.error("missing tileGid " + art);
        platformArt.add(art);
        platformVisible.add(o.bool("visible", true) && off[2] > 0);
        surfaces.put(name, platforms.size());
        platforms.add(new AreaDefinition.Platform(x, x + w, y, false, h));
      }
    }
    String ground = required(mp, "groundSurface", map);
    if (!surfaces.containsKey(ground))
      throw map.error("groundSurface references missing platform " + ground);
    // Surface 0 is the legacy navigation root. Keep the ground object first in Tiled.
    if (surfaces.get(ground) != 0)
      throw map.error("groundSurface must be the first platform/collision object");
    List<AreaDefinition.Ladder> ladders = new ArrayList<>();
    List<AreaDefinition.Link> links = new ArrayList<>();
    Map<String, LevelDef.Spawn> starts = new LinkedHashMap<>();
    List<LevelDef.Spawn> enemies = new ArrayList<>();
    List<LevelDef.Pickup> pickups = new ArrayList<>();
    List<LevelDef.Exit> exits = new ArrayList<>();
    List<LevelDef.Region> regions = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      Json.Node o = objects.get(i);
      Map<String, Json.Node> p = props(o);
      String type = type(o), name = o.str("name", "");
      float[] off = objectOffsets.get(i);
      float x = (float) o.num("x", 0) + off[0],
          y = (float) o.num("y", 0) + off[1],
          w = (float) o.num("width", 0),
          h = (float) o.num("height", 0);
      bounds(o, x, y, w, h, width, height);
      switch (type) {
        case "platform":
        case "collision":
          break;
        case "ladder":
          {
            properties(p, o, "bottom", "top", "openingWidth", "entryRadius", "tileGid", "tileId");
            int b = surface(surfaces, required(p, "bottom", o), o),
                t = surface(surfaces, required(p, "top", o), o);
            if (w <= 0 || h <= 0) throw o.error("ladder must be a rectangle");
            float cx = x + w / 2, top = platforms.get(t).top(), bottom = platforms.get(b).top();
            if (Math.abs(y - top) > .1 || Math.abs(y + h - bottom) > .1)
              throw o.error("ladder rectangle top/bottom must meet referenced platform tops");
            float opening = number(p, "openingWidth", w + 32), entry = number(p, "entryRadius", 30);
            if (opening < w || entry <= 0 || entry > opening / 2)
              throw o.error("invalid openingWidth/entryRadius");
            if (cx - opening / 2 < platforms.get(t).left()
                || cx + opening / 2 > platforms.get(t).right())
              throw o.error("ladder opening must fit inside top platform");
            int art = tileRef(p, tileIds, o);
            if (art != 0 && !tiles.containsKey(art)) throw o.error("missing tileGid " + art);
            decorations.add(
                new LevelDef.Decoration(art, x, y, w, h, w, o.bool("visible", true) && off[2] > 0));
            ladders.add(new AreaDefinition.Ladder(cx, b, t, w, opening, entry));
            links.add(new AreaDefinition.Link(b, t, AreaDefinition.Travel.LADDER, cx, cx));
            links.add(new AreaDefinition.Link(t, b, AreaDefinition.Travel.LADDER, cx, cx));
            break;
          }
        case "navigation":
          {
            properties(p, o, "from", "to", "travel", "destinationX", "bidirectional");
            int a = surface(surfaces, required(p, "from", o), o),
                b = surface(surfaces, required(p, "to", o), o);
            String travel = required(p, "travel", o);
            AreaDefinition.Travel tt;
            try {
              tt = AreaDefinition.Travel.valueOf(travel.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
              throw o.error("travel must be walk, jump or drop");
            }
            if (tt == AreaDefinition.Travel.LADDER) throw o.error("use a ladder object");
            float dest = number(p, "destinationX", x);
            if (dest < 0 || dest > width) throw o.error("destinationX must be inside the world");
            links.add(new AreaDefinition.Link(a, b, tt, x, dest));
            if (flag(p, "bidirectional", true))
              links.add(new AreaDefinition.Link(b, a, tt, dest, x));
            break;
          }
        case "player_spawn":
        case "enemy_spawn":
          {
            properties(p, o, "entityId", "facing");
            if (name.isEmpty()) throw o.error("spawn needs a stable name");
            String entity = required(p, "entityId", o),
                facing = prop(p, "facing", type.equals("player_spawn") ? "right" : "left");
            if (!facing.equals("right") && !facing.equals("left"))
              throw o.error("facing must be left or right");
            LevelDef.Spawn s = new LevelDef.Spawn(name, entity, x, y, facing.equals("right"));
            if (type.equals("player_spawn")) starts.put(name, s);
            else enemies.add(s);
            break;
          }
        case "pickup":
          properties(p, o, "kind", "amount");
          String kind = required(p, "kind", o);
          if (!kind.equals("health") && !kind.equals("power"))
            throw o.error("pickup kind must be health or power");
          int amount = p.containsKey("amount") ? p.get("amount").integer(1, 1000000) : 1;
          if (w <= 0 || h <= 0 || name.isEmpty())
            throw o.error("pickup needs name and positive rectangle");
          pickups.add(new LevelDef.Pickup(name, kind, x, y, w, h, amount));
          break;
        case "exit":
          properties(p, o, "levelId", "spawnId");
          if (w <= 0 || h <= 0) throw o.error("exit requires rectangle");
          exits.add(new LevelDef.Exit(x, y, w, h, prop(p, "levelId", ""), prop(p, "spawnId", "")));
          break;
        case "decoration":
          properties(p, o, "tileGid", "tileId", "tileSize");
          int art = tileRef(p, tileIds, o);
          if (!tiles.containsKey(art) || w <= 0 || h <= 0)
            throw o.error("decoration needs valid tileGid and positive rectangle");
          float size = number(p, "tileSize", 32);
          if (size <= 0) throw o.error("tileSize must be positive");
          decorations.add(
              new LevelDef.Decoration(
                  art, x, y, w, h, size, o.bool("visible", true) && off[2] > 0));
          break;
        case "region":
          properties(p, o);
          regions.add(new LevelDef.Region(name, x, y, w, h));
          break;
        default:
          throw o.error(
              "unsupported object type '"
                  + type
                  + "'; expected platform, collision, ladder, navigation, player_spawn,"
                  + " enemy_spawn, pickup, exit or region");
      }
    }
    for (int i = 0; i < platforms.size(); i++) {
      int firstSolid = solids.size();
      var deck = platforms.get(i);
      List<AreaDefinition.Ladder> holes = new ArrayList<>();
      for (var l : ladders) if (l.top() == i) holes.add(l);
      Collections.sort(holes, (a, b) -> Float.compare(a.x(), b.x()));
      float cursor = deck.left();
      for (var l : holes) {
        float left = l.x() - l.openingWidth() / 2;
        if (left < cursor) throw map.error("overlapping ladder apertures");
        if (left > cursor)
          solids.add(
              new AreaDefinition.Solid(cursor, left, deck.top(), deck.top() + deck.thickness()));
        cursor = l.x() + l.openingWidth() / 2;
      }
      if (cursor < deck.right())
        solids.add(
            new AreaDefinition.Solid(
                cursor, deck.right(), deck.top(), deck.top() + deck.thickness()));
      for (int j = firstSolid; j < solids.size(); j++) {
        var q = solids.get(j);
        decorations.add(
            new LevelDef.Decoration(
                platformArt.get(i),
                q.left(),
                q.top(),
                q.right() - q.left(),
                q.bottom() - q.top(),
                32,
                platformVisible.get(i)));
      }
    }
    // Tile solidity is opt-in in .tsj; decorative art never creates invisible collision.
    for (LevelDef.Layer layer : layers)
      if (layer.visible())
        for (int i = 0; i < layer.gids().size(); i++) {
          int gid = (int) (layer.gids().get(i) & 0x0fffffffL);
          var tile = tiles.get(gid);
          if (tile != null && tile.solid()) {
            float x = layer.x() + i % layer.width() * tw, y = layer.y() + i / layer.width() * th;
            solids.add(new AreaDefinition.Solid(x, x + tw, y, y + th));
          }
        }
    AreaDefinition.Spawn[] sp = new AreaDefinition.Spawn[enemies.size()];
    for (int i = 0; i < sp.length; i++) {
      var s = enemies.get(i);
      sp[i] = new AreaDefinition.Spawn(s.x(), 0);
    }
    AreaDefinition area =
        new AreaDefinition(
            prop(mp, "name", id),
            platforms.toArray(new AreaDefinition.Platform[0]),
            ladders.toArray(new AreaDefinition.Ladder[0]),
            sp,
            links.toArray(new AreaDefinition.Link[0]),
            width,
            height,
            platforms.get(0).top(),
            true,
            solids.toArray(new AreaDefinition.Solid[0]));
    String start = required(mp, "startSpawn", map), player = required(mp, "playerEntity", map);
    if (!starts.containsKey(start))
      throw map.error("startSpawn references missing player_spawn " + start);
    String background = prop(mp, "backgroundImage", "");
    if (!background.isEmpty()) background = ContentSource.path(file, background);
    return new LevelDef(
        id,
        file,
        player,
        start,
        area,
        starts,
        enemies,
        pickups,
        exits,
        regions,
        layers,
        tiles,
        tw,
        th,
        background,
        decorations);
  }

  private static int tileRef(
      Map<String, Json.Node> p, Map<String, Integer> ids, Json.Node context) {
    if (p.containsKey("tileId")) {
      if (p.containsKey("tileGid")) throw context.error("use tileId or tileGid, not both");
      String id = p.get("tileId").string();
      Integer gid = ids.get(id);
      if (gid == null) throw context.error("missing tileId " + id);
      return gid;
    }
    return p.containsKey("tileGid") ? p.get("tileGid").integer(0, 1000000) : 0;
  }

  private static String type(Json.Node n) {
    String c = n.str("class", "");
    return c.isEmpty() ? n.str("type", "") : c;
  }

  private static int surface(Map<String, Integer> s, String id, Json.Node n) {
    Integer i = s.get(id);
    if (i == null) throw n.error("missing platform reference '" + id + "'");
    return i;
  }

  private static void bounds(Json.Node n, float x, float y, float w, float h, float mw, float mh) {
    if (!Float.isFinite(x + y + w + h)
        || x < 0
        || y < 0
        || w < 0
        || h < 0
        || x + w > mw + .01
        || y + h > mh + .01) throw n.error("object outside map bounds");
  }

  private void layers(
      Json.Node nodes,
      float ox,
      float oy,
      boolean visible,
      float opacity,
      List<LevelDef.Layer> layers,
      List<Json.Node> objects,
      List<float[]> offsets,
      Map<Integer, LevelDef.Tile> tiles) {
    for (Json.Node l : nodes.list()) {
      float x = ox + (float) l.num("offsetx", 0), y = oy + (float) l.num("offsety", 0);
      boolean vis = visible && l.bool("visible", true);
      float alpha = opacity * (float) l.num("opacity", 1);
      if (alpha < 0 || alpha > 1) throw l.error("opacity must be 0..1");
      if (l.num("parallaxx", 1) != 1 || l.num("parallaxy", 1) != 1)
        throw l.error("Tiled per-layer parallax is unsupported");
      switch (l.str("type")) {
        case "group":
          layers(l.get("layers"), x, y, vis, alpha, layers, objects, offsets, tiles);
          break;
        case "tilelayer":
          {
            if (l.has("encoding") || l.has("compression") || l.has("chunks"))
              throw l.error("save tile data as uncompressed JSON array");
            if (l.num("x", 0) != 0 || l.num("y", 0) != 0)
              throw l.error("use layer offsetx/offsety rather than tile origin offsets");
            int w = l.get("width").integer(1, 1024), h = l.get("height").integer(1, 1024);
            List<Json.Node> data = l.get("data").list();
            if (w * (long) h > 262144)
              throw l.error("tile layer exceeds 262144-cell budget; split into smaller levels");
            if (data.size() != w * h) throw l.error("tile data length differs from width*height");
            List<Long> gids = new ArrayList<>();
            for (Json.Node g : data) {
              double n = g.range(0, 4294967295d);
              if (n != Math.rint(n)) throw g.error("GID must be integer");
              long raw = (long) n;
              int gid = (int) (raw & 0x0fffffffL);
              if ((raw & 0x30000000L) != 0)
                throw g.error(
                    "diagonal/hexagonal tile rotation is unsupported; use horizontal/vertical"
                        + " flip");
              if (gid != 0 && !tiles.containsKey(gid))
                throw g.error("missing tileset for GID " + gid);
              gids.add(raw);
            }
            layers.add(
                new LevelDef.Layer(
                    l.str("name", ""), w, h, x, y, vis, alpha, Collections.unmodifiableList(gids)));
            break;
          }
        case "objectgroup":
          for (Json.Node o : l.get("objects").list()) {
            if (o.num("rotation", 0) != 0
                || o.has("ellipse")
                || o.has("polygon")
                || o.has("polyline")
                || o.has("gid")
                || o.has("template")
                || o.has("text"))
              throw o.error("only unrotated rectangles and point objects are supported");
            objects.add(o);
            offsets.add(new float[] {x, y, vis ? 1 : 0});
          }
          break;
        default:
          throw l.error("unsupported layer type " + l.str("type"));
      }
    }
  }
}
