package com.lockin.game.content;

import com.lockin.game.AreaDefinition;
import java.util.*;

/** Parsed finite orthogonal Tiled map. World pixels equal Tiled pixels; Y increases down. */
public final class LevelDef {
  public record Spawn(String id, String entity, float x, float feet, boolean right) {}

  public record Pickup(
      String id, String kind, float x, float y, float width, float height, int amount) {}

  public record Exit(float x, float y, float width, float height, String level, String spawn) {}

  public record Region(String name, float x, float y, float width, float height) {}

  public record Tile(int gid, String asset, int sx, int sy, int width, int height, boolean solid) {}

  public record Decoration(
      int gid, float x, float y, float width, float height, float tileSize, boolean visible) {}

  public record Layer(
      String name,
      int width,
      int height,
      float x,
      float y,
      boolean visible,
      float opacity,
      List<Long> gids) {}

  public final String id, file, playerEntity, startSpawn;
  public final AreaDefinition area;
  public final Map<String, Spawn> playerSpawns;
  public final List<Spawn> enemies;
  public final List<Pickup> pickups;
  public final List<Exit> exits;
  public final List<Region> regions;
  public final List<Layer> layers;
  public final Map<Integer, Tile> tiles;
  public final int tileWidth, tileHeight;
  public final String background;
  public final List<Decoration> decorations;

  public LevelDef(
      String id,
      String file,
      String playerEntity,
      String startSpawn,
      AreaDefinition area,
      Map<String, Spawn> spawns,
      List<Spawn> enemies,
      List<Pickup> pickups,
      List<Exit> exits,
      List<Region> regions,
      List<Layer> layers,
      Map<Integer, Tile> tiles,
      int tw,
      int th,
      String background,
      List<Decoration> decorations) {
    this.id = id;
    this.file = file;
    this.playerEntity = playerEntity;
    this.startSpawn = startSpawn;
    this.area = area;
    playerSpawns = Collections.unmodifiableMap(new LinkedHashMap<>(spawns));
    this.enemies = Collections.unmodifiableList(new ArrayList<>(enemies));
    this.pickups = Collections.unmodifiableList(new ArrayList<>(pickups));
    this.exits = Collections.unmodifiableList(new ArrayList<>(exits));
    this.regions = Collections.unmodifiableList(new ArrayList<>(regions));
    this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
    this.tiles = Collections.unmodifiableMap(new LinkedHashMap<>(tiles));
    tileWidth = tw;
    tileHeight = th;
    this.background = background;
    this.decorations = Collections.unmodifiableList(new ArrayList<>(decorations));
  }
}
