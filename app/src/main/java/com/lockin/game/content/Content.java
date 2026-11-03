package com.lockin.game.content;

import com.lockin.game.CombatConfig;
import java.io.*;
import java.util.*;

/** Load, resolve and validate the whole pack once before starting a run. No Android dependency. */
public final class Content {
  public final Map<String, DefenseDef> defenses;
  public final Map<String, EntityDef> entities;
  public final Map<String, AttackDef> attacks;
  public final Map<String, ComboDef> combos;
  public final Map<String, AnimationDef> animations;
  public final Map<String, BehaviorDef> behaviors;
  public final Map<String, LevelDef> levels;
  public final Map<String, BossDef> bosses;
  public final Map<String, ItemDef> items;
  public final Map<String, Integer> initialItems;
  public final int accessorySlots, quickSlots;
  public final String startLevel;

  private Content(Loader l, String start) {
    defenses = freeze(l.defenses);
    entities = freeze(l.entities);
    attacks = freeze(l.attacks);
    combos = freeze(l.combos);
    animations = freeze(l.animations);
    behaviors = freeze(l.behaviors);
    levels = freeze(l.levels);
    bosses=freeze(l.bosses);
    items=freeze(l.items); initialItems=freeze(l.initialItems);
    accessorySlots=l.accessorySlots; quickSlots=l.quickSlots;
    startLevel = start;
  }

  private static <T> Map<String, T> freeze(Map<String, T> m) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(m));
  }

  public static Content load(ContentSource source) throws IOException {
    return new Loader(source).load();
  }

  private static final class Loader {
    final ContentSource source;
    final Map<String, DefenseDef> defenses = new LinkedHashMap<>();
    final Map<String, EntityDef> entities = new LinkedHashMap<>();
    final Map<String, AttackDef> attacks = new LinkedHashMap<>();
    final Map<String, ComboDef> combos = new LinkedHashMap<>();
    final Map<String, AnimationDef> animations = new LinkedHashMap<>();
    final Map<String, BehaviorDef> behaviors = new LinkedHashMap<>();
    final Map<String, LevelDef> levels = new LinkedHashMap<>();
    final Map<String, int[]> images = new HashMap<>();
    final Map<String,BossDef> bosses=new LinkedHashMap<>();
    final Map<String,ItemDef> items=new LinkedHashMap<>();
    final Map<String,Integer> initialItems=new LinkedHashMap<>();
    int accessorySlots=1, quickSlots=2;
    private long imagePixels;

    Loader(ContentSource source) {
      this.source = source;
    }

    Json.Node read(String file) throws IOException {
      return Json.parse(file, source.text(file));
    }

    <T> void put(Map<String, T> map, String id, T value, Json.Node n) {
      if (!id.matches("[A-Za-z0-9_.-]+"))
        throw n.error("ID must contain only letters, digits, _, . or -");
      if (map.put(id, value) != null) throw n.error("duplicate ID " + id);
    }

    <T> T ref(Map<String, T> map, String id, String where) {
      T t = map.get(id);
      if (t == null) throw new IllegalArgumentException(where + ": missing reference '" + id + "'");
      return t;
    }

    List<String> strings(Json.Node n) {
      List<String> out = new ArrayList<>();
      for (Json.Node v : n.list()) out.add(v.string());
      return out;
    }

    List<String> files(Json.Node n, String key) {
      List<String> out = new ArrayList<>();
      for (String f : strings(n.get(key))) out.add(ContentSource.path("manifest.json", f));
      return out;
    }

    Content load() throws IOException {
      Json.Node m = read("manifest.json");
      m.only(
          "items", "inventory", "bosses",
          "schemaVersion",
          "startLevel",
          "defenses",
          "entities",
          "attacks",
          "combos",
          "animations",
          "behaviors",
          "levels");
      if (m.get("schemaVersion").integer(1, 1) != 1) throw m.error("unsupported schema");
      for (String f : files(m, "defenses")) loadDefenses(f);
      for (String f : files(m, "animations")) loadAnimations(f);
      for (String f : files(m, "attacks")) loadAttacks(f);
      for (String f : files(m, "combos")) loadCombos(f);
      for (String f : files(m, "behaviors")) loadBehaviors(f);
      for (String f : files(m, "entities")) loadEntity(f);
      if(m.has("items")) for(String f:files(m,"items")) loadItems(f);
      if(m.has("inventory")) {
        Json.Node inventory=m.get("inventory");
        inventory.only("accessorySlots","quickSlots","initialItems");
        accessorySlots=inventory.has("accessorySlots")?inventory.get("accessorySlots").integer(0,8):1;
        quickSlots=inventory.has("quickSlots")?inventory.get("quickSlots").integer(0,8):2;
        if(inventory.has("initialItems")) for(Json.Node grant:inventory.get("initialItems").list()) {
          grant.only("id","quantity"); String id=grant.str("id");
          ItemDef item=ref(items,id,grant.path);
          int quantity=grant.get("quantity").integer(1,item.stackLimit);
          if(initialItems.put(id,quantity)!=null) throw grant.error("duplicate initial item");
        }
      }
      TiledLoader tiled = new TiledLoader(source);
      for (Json.Node v : m.get("levels").list()) {
        v.only("id", "file");
        String id = v.str("id"), file = ContentSource.path("manifest.json", v.str("file"));
        if (!file.endsWith(".tmj")) throw v.error("level must use .tmj");
        put(levels, id, tiled.load(id, file), v);
      }
      for (AnimationDef a : animations.values())
        if (!a.next.isEmpty()) ref(animations, a.next, "animation " + a.id + ".next");
      long totalCells = 0;
      for (LevelDef l : levels.values()) {
        for (var layer : l.layers) totalCells += layer.gids().size();
        if (totalCells > 1_048_576)
          throw new IllegalArgumentException(l.file + ": pack exceeds 1048576 tile cells");
        if (!l.background.isEmpty()) image(l.background);
        EntityDef player = ref(entities, l.playerEntity, l.file + ".playerEntity");
        if (!player.kind.equals("player"))
          throw new IllegalArgumentException(l.file + ": playerEntity must reference a player");
        for (LevelDef.Spawn s : l.playerSpawns.values()) {
          if (!s.entity().equals(l.playerEntity))
            throw new IllegalArgumentException(
                l.file + "." + s.id() + ": all player spawns must use playerEntity");
          spawn(l, s, player);
        }
        for (LevelDef.Spawn s : l.enemies) {
          EntityDef e = ref(entities, s.entity(), l.file + "." + s.id() + ".entityId");
          if (!e.kind.equals("enemy"))
            throw new IllegalArgumentException(
                l.file + "." + s.id() + ": enemy_spawn must reference enemy kind");
          spawn(l, s, e);
        }
        if (l.enemies.size() > 128)
          throw new IllegalArgumentException(l.file + ": max 128 enemy spawns per level");
        for (LevelDef.Tile t : l.tiles.values()) {
          int[] d = image(t.asset());
          if (t.sx() + t.width() > d[0] || t.sy() + t.height() > d[1])
            throw new IllegalArgumentException(l.file + ": tile rectangle exceeds " + t.asset());
        }
        for(LevelDef.Pickup pickup:l.pickups) if(pickup.kind().equals("item")) {
          ItemDef item=ref(items,pickup.itemId(),l.file+".pickup.itemId");
          if(pickup.amount()>item.stackLimit) throw new IllegalArgumentException(l.file+": pickup exceeds item stackLimit");
        }
        for (LevelDef.Exit e : l.exits)
          if (!e.level().isEmpty()) {
            LevelDef target = ref(levels, e.level(), l.file + ".exit.levelId");
            if (!target.playerEntity.equals(l.playerEntity))
              throw new IllegalArgumentException(
                  l.file + ": transitions require the same playerEntity");
            if (!target.playerSpawns.containsKey(e.spawn()))
              throw new IllegalArgumentException(
                  l.file + ": exit references missing spawn " + e.spawn());
          }
      }
      if(m.has("bosses")) for(String file:files(m,"bosses")) {
        Json.Node root=read(file);root.only("bosses");
        for(Json.Node n:root.get("bosses").list()) {
          BossDef b=new BossDef(n);LevelDef level=ref(levels,b.level,n.path+".level");
          boolean found=false;
          for(var spawn:level.enemies)if(spawn.id().equals(b.spawn))found=true;
          if(!found)throw n.error("missing boss enemy spawn "+b.spawn);
          for(var existing:bosses.values())if(existing.level.equals(b.level)&&existing.spawn.equals(b.spawn))
            throw n.error("enemy spawn already belongs to encounter");
          if(b.x+b.width>level.area.width || b.y+b.height>level.area.height)throw n.error("trigger outside level");
          for(var phase:b.phases) {
            ref(behaviors,phase.behavior(),n.path+".phases.behavior");
            if(!phase.animation().isEmpty())ref(animations,phase.animation(),n.path+".phases.animation");
          }
          for(var reward:b.rewards)if(reward.quantity()>ref(items,reward.item(),n.path+".rewards.item").stackLimit)
            throw n.error("reward quantity exceeds stackLimit");
          put(bosses,b.id,b,n);
        }
      }
      String start = m.str("startLevel");
      ref(levels, start, "manifest.json.startLevel");
      return new Content(this, start);
    }

    void spawn(LevelDef l, LevelDef.Spawn s, EntityDef e) {
      String where = l.file + "." + s.id();
      if (s.x() < 0
          || s.x() + e.width > l.area.width
          || s.feet() - e.height < 0
          || s.feet() > l.area.height)
        throw new IllegalArgumentException(where + ": body lies outside map");
      for (int i = 0; i < l.area.solidCount(); i++) {
        var solid = l.area.solid(i);
        if (s.x() < solid.right() - .001
            && s.x() + e.width > solid.left() + .001
            && s.feet() - e.height < solid.bottom() - .001
            && s.feet() > solid.top() + .001)
          throw new IllegalArgumentException(
              where + ": spawn body overlaps solid terrain; set point Y to floor top (feet)");
      }
      for (int i = 0; i < l.area.ladderCount(); i++)
        if (e.width >= l.area.ladder(i).openingWidth())
          throw new IllegalArgumentException(
              where + ": body width must be smaller than ladder openingWidth");
    }

    int[] image(String path) throws IOException {
      int[] cached = images.get(path);
      if (cached != null) return cached;
      try (DataInputStream in = new DataInputStream(source.open(path))) {
        if (in.readLong() != 0x89504e470d0a1a0aL
            || in.readInt() != 13
            || in.readInt() != 0x49484452) throw new IOException(path + ": expected PNG image");
        int w = in.readInt(), h = in.readInt();
        if (w <= 0 || h <= 0 || w > 16384 || h > 16384)
          throw new IOException(path + ": invalid image dimensions");
        imagePixels += (long) w * h;
        if (imagePixels > 16_777_216)
          throw new IOException(
              path
                  + ": referenced PNGs exceed the 64 MiB decoded-image budget; reduce atlas"
                  + " dimensions or split the pack");
        cached = new int[] {w, h};
        images.put(path, cached);
        return cached;
      } catch (IOException e) {
        throw new IOException(path + ": missing or invalid image (" + e.getMessage() + ")", e);
      }
    }

    void loadItems(String file) throws IOException {
      Json.Node root=read(file); root.only("items");
      for(Json.Node n:root.get("items").list()) {
        String icon=n.str("icon","");
        if(!icon.isEmpty()) {icon=ContentSource.path(file,icon); image(icon);}
        Map<String,String> overrides=new LinkedHashMap<>();
        if(n.has("animations")) {
          Json.Node states=n.get("animations");
          states.only("idle","run","jump","fall","guard","deflect","dash","hurt","stagger","death","deathblow");
          for(String state:states.keys()) {
            String id=states.str(state); ref(animations,id,states.path+"."+state); overrides.put(state,id);
          }
        }
        ItemDef item=new ItemDef(n,icon,overrides);
        if(!item.attack.isEmpty()) ref(attacks,item.attack,n.path+".attack");
        if(!item.combo.isEmpty()) ref(combos,item.combo,n.path+".combo");
        put(items,item.id,item,n);
      }
    }

    void loadAnimations(String file) throws IOException {
      Json.Node root = read(file);
      root.only("animations");
      for (Json.Node n : root.get("animations").list()) {
        n.only(
            "id",
            "asset",
            "frameWidth",
            "frameHeight",
            "columns",
            "frames",
            "fps",
            "loop",
            "facesRight",
            "anchorX",
            "anchorY",
            "scale",
            "events",
            "next",
            "placeholder");
        String id = n.str("id"), asset = n.str("asset", "");
        boolean placeholder = n.bool("placeholder", false);
        double fps = n.get("fps").range(.01, 1000);
        int fw = n.get("frameWidth").integer(1, 4096),
            fh = n.get("frameHeight").integer(1, 4096),
            cols = n.get("columns").integer(1, 4096);
        List<AnimationDef.Frame> frames = new ArrayList<>();
        for (Json.Node f : n.get("frames").list()) {
          int x, y, w = fw, h = fh;
          String path = asset;
          double seconds = 1 / fps;
          try {
            int index = f.integer(0, 1000000);
            x = index % cols * fw;
            y = index / cols * fh;
          } catch (IllegalArgumentException numberError) {
            // Object frames support an explicit source rectangle or a separate source file.
            f.only("index", "asset", "x", "y", "width", "height", "duration");
            int index = f.has("index") ? f.get("index").integer(0, 1000000) : 0;
            x = f.has("x") ? f.get("x").integer(0, 16384) : index % cols * fw;
            y = f.has("y") ? f.get("y").integer(0, 16384) : index / cols * fh;
            w = f.has("width") ? f.get("width").integer(1, 4096) : fw;
            h = f.has("height") ? f.get("height").integer(1, 4096) : fh;
            path = f.str("asset", asset);
            seconds = f.has("duration") ? f.get("duration").range(.001, 60) : seconds;
          }
          if (!placeholder) {
            path = ContentSource.path(file, path);
            int[] size = image(path);
            if (x + w > size[0] || y + h > size[1])
              throw f.error(
                  "frame rectangle exceeds " + path + " (" + size[0] + "x" + size[1] + ")");
          }
          frames.add(new AnimationDef.Frame(path, x, y, w, h, seconds));
        }
        if (frames.isEmpty() || frames.size() > 1024)
          throw n.error("frames must contain 1..1024 entries");
        double duration = 0;
        for (var f : frames) duration += f.seconds();
        List<AnimationDef.Event> events = new ArrayList<>();
        if (n.has("events"))
          for (Json.Node e : n.get("events").list()) {
            e.only("name", "at");
            String name = e.str("name");
            if (name.startsWith("hitbox_"))
              throw e.error(
                  "combat hitbox events belong in attacks.json; animation events are"
                      + " presentation-only");
            events.add(new AnimationDef.Event(name, e.get("at").range(0, duration)));
          }
        Collections.sort(events, (a, b) -> Double.compare(a.at(), b.at()));
        put(
            animations,
            id,
            new AnimationDef(
                id,
                frames,
                events,
                n.f("anchorX", -4096, 4096),
                n.f("anchorY", -4096, 4096),
                n.f("scale", .01f, 30),
                n.bool("loop", false),
                n.bool("facesRight", true),
                n.str("next", ""),
                placeholder),
            n);
      }
    }

    void loadAttacks(String file) throws IOException {
      Json.Node root = read(file);
      root.only("attacks");
      for (Json.Node n : root.get("attacks").list()) {
        n.only(
            "id",
            "style",
            "damage",
            "postureDamage",
            "blockDamage",
            "deflectDamage",
            "duration",
            "cooldown",
            "directional",
            "blockable",
            "deflectable",
            "interruptOnHit",
            "hits",
            "dashCancel", "motion", "combat",
            "recoveryAnimation");
        double duration = n.get("duration").range(.008333, 60),
            cooldown = n.get("cooldown").range(0, 60);
        List<AttackDef.Hit> hits = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        double end = 0;
        for (Json.Node h : n.get("hits").list()) {
          h.only(
              "id",
              "start",
              "end",
              "forward",
              "top",
              "width",
              "height",
              "windupAnimation",
              "activeAnimation",
              "damage", "postureDamage", "blockDamage", "deflectDamage");
          String hi = h.str("id");
          if (!ids.add(hi)) throw h.error("duplicate hit ID " + hi);
          double start = h.get("start").range(end, duration),
              stop = h.get("end").range(start + 1.0 / 120, duration);
          end = stop;
          String wa = h.str("windupAnimation"), aa = h.str("activeAnimation");
          ref(animations, wa, h.path + ".windupAnimation");
          ref(animations, aa, h.path + ".activeAnimation");
          hits.add(
              new AttackDef.Hit(
                  hi,
                  start,
                  stop,
                  h.f("forward", -2000, 2000),
                  h.f("top", -2000, 2000),
                  h.f("width", 1, 2000),
                  h.f("height", 1, 2000),
                  wa,
                  aa,
                  h.has("damage") ? h.get("damage").integer(0, 1000000000) : -1,
                  h.has("postureDamage") ? h.get("postureDamage").range(0, 1e9) : -1,
                  h.has("blockDamage") ? h.get("blockDamage").range(0, 1e9) : -1,
                  h.has("deflectDamage") ? h.get("deflectDamage").range(0, 1e9) : -1));
        }
        if (hits.isEmpty() || hits.size() > 32)
          throw n.error("requires 1..32 ordered non-overlapping hits");
        List<AttackDef.Window> cancel = new ArrayList<>();
        for (Json.Node w : n.get("dashCancel").list()) {
          w.only("start", "end");
          double start = w.get("start").range(0, duration);
          cancel.add(new AttackDef.Window(start, w.get("end").range(start, duration)));
        }
        String recovery = n.str("recoveryAnimation"), style = n.str("style");
        ref(animations, recovery, n.path + ".recoveryAnimation");
        if (!Arrays.asList("normal", "double", "heavy").contains(style))
          throw n.error("style must be normal, double or heavy (telegraph style only)");
        put(
            attacks,
            n.str("id"),
            new AttackDef(
                n.str("id"),
                style,
                n.get("damage").integer(0, 1000000000),
                n.get("postureDamage").range(0, 1e9),
                n.get("blockDamage").range(0, 1e9),
                n.get("deflectDamage").range(0, 1e9),
                duration,
                cooldown,
                n.bool("directional", true),
                n.bool("blockable", true),
                n.bool("deflectable", true),
                n.bool("interruptOnHit", false),
                hits,
                cancel,
                recovery,
                n.has("motion")?AttackDef.Motion.parse(n.get("motion"),duration):AttackDef.Motion.NONE,
                n.has("combat")?new CombatRules(n.get("combat"),duration):CombatRules.NONE),
            n);
      }
    }

    void loadCombos(String file) throws IOException {
      Json.Node root = read(file);
      root.only("combos");
      for (Json.Node n : root.get("combos").list()) {
        n.only("id", "sequence", "followups");
        List<String> sequence = strings(n.get("sequence"));
        if (sequence.isEmpty()) throw n.error("sequence must not be empty");
        for (String s : sequence) ref(attacks, s, n.path + ".sequence");
        List<ComboDef.Followup> followups = new ArrayList<>();
        Set<String> from = new HashSet<>();
        for (Json.Node f : n.get("followups").list()) {
          f.only("from", "to", "open", "close", "executeAt");
          String a = f.str("from"), b = f.str("to");
          AttackDef attack = ref(attacks, a, f.path + ".from");
          ref(attacks, b, f.path + ".to");
          if (!from.add(a)) throw f.error("one ATTACK follow-up per source attack is supported");
          double open = f.get("open").range(0, attack.duration),
              close = f.get("close").range(open, attack.duration),
              at = f.get("executeAt").range(close, attack.duration);
          followups.add(new ComboDef.Followup(a, b, open, close, at));
        }
        put(combos, n.str("id"), new ComboDef(n.str("id"), sequence, followups), n);
      }
    }

    void loadBehaviors(String file) throws IOException {
      Json.Node root = read(file);
      root.only("behaviors");
      for (Json.Node n : root.get("behaviors").list()) {
        n.only(
            "id",
            "type",
            "selection",
            "combo",
            "weights",
            "detectionRadius",
            "engagementDistance",
            "betweenMoves",
            "staggerRecovery",
            "chase",
            "facePlayer",
            "seed",
            "transitions");
        if (!n.str("type").equals("melee")) throw n.error("supported behavior type is melee");
        String selection = n.str("selection");
        if (!selection.equals("sequence") && !selection.equals("weighted"))
          throw n.error("selection must be sequence or weighted");
        ComboDef c = ref(combos, n.str("combo"), n.path + ".combo");
        List<Double> weights = new ArrayList<>();
        for (Json.Node v : n.get("weights").list()) weights.add(v.range(0, 1000000));
        if (weights.size() != c.sequence.size())
          throw n.error("weights length must match combo.sequence");
        double sum = 0;
        for (double w : weights) sum += w;
        if (sum <= 0) throw n.error("at least one weight must be positive");
        // Conditions are a closed vocabulary; configuration toggles chase/facing and thresholds.
        List<String> transitions = strings(n.get("transitions"));
        if (!transitions.equals(
            Arrays.asList(
                "detect", "in_melee_range", "timeline_complete", "posture_broken", "health_zero")))
          throw n.error(
              "transitions must list detect, in_melee_range, timeline_complete, posture_broken,"
                  + " health_zero in that order");
        put(
            behaviors,
            n.str("id"),
            new BehaviorDef(
                n.str("id"),
                selection,
                c.sequence,
                weights,
                n.f("detectionRadius", 0, 32768),
                n.f("engagementDistance", 0, 2000),
                n.get("betweenMoves").range(0, 60),
                n.get("staggerRecovery").range(0, 60),
                n.bool("chase", true),
                n.bool("facePlayer", true),
                n.get("seed").integer(1, Integer.MAX_VALUE)),
            n);
      }
    }

    void loadDefenses(String file) throws IOException {
      Json.Node root = read(file);
      root.only("defenses");
      for (Json.Node c : root.get("defenses").list()) {
        c.only(
            "id",
            "deflectWindow",
            "recoveryDelay",
            "brokenDuration",
            "spamStep",
            "minimumDeflectWindow",
            "spamRecovery",
            "breakImmunity",
            "deathblowRange",
            "blockPostureMultiplier",
            "deflectPostureMultiplier");
        CombatConfig config;
        try {
          config =
              new CombatConfig(
                  c.get("deflectWindow").range(0, 10),
                  100,
                  12,
                  0,
                  0,
                  c.get("recoveryDelay").range(0, 60),
                  c.get("brokenDuration").range(1.0 / 120, 60),
                  c.get("spamStep").range(0, 10),
                  c.get("minimumDeflectWindow").range(0, 10),
                  c.get("spamRecovery").range(.001, 60),
                  c.get("breakImmunity").range(.001, 60),
                  c.get("deathblowRange").range(0, 2000));
        } catch (IllegalArgumentException e) {
          throw c.error(e.getMessage());
        }
        put(
            defenses,
            c.str("id"),
            new DefenseDef(
                c.str("id"),
                config,
                c.get("blockPostureMultiplier").range(0, 100),
                c.get("deflectPostureMultiplier").range(0, 100)),
            c);
      }
    }

    void loadEntity(String file) throws IOException {
      Json.Node n = read(file);
      n.only(
          "id",
          "kind",
          "staggerResistance",
          "maxHealth",
          "width",
          "height",
          "speed",
          "acceleration",
          "jumpSpeed",
          "gravity",
          "climbSpeed",
          "airSpeed",
          "dash",
          "deathblowDuration",
          "combatProfile",
          "maxPosture",
          "postureRecovery",
          "attack",
          "combo",
          "behavior",
          "animations");
      String kind = n.str("kind");
      if (!kind.equals("player") && !kind.equals("enemy"))
        throw n.error("kind must be player or enemy");
      ref(attacks, n.str("attack"), n.path + ".attack");
      if (!n.str("combo", "").isEmpty()) ref(combos, n.str("combo"), n.path + ".combo");
      if (kind.equals("enemy")) ref(behaviors, n.str("behavior"), n.path + ".behavior");
      DefenseDef defense = ref(defenses, n.str("combatProfile"), n.path + ".combatProfile");
      CombatConfig config =
          defense.forEntity(
              n.get("maxPosture").range(1, 1e9), n.get("postureRecovery").range(0, 1e9));
      Map<String, String> anim = new LinkedHashMap<>();
      Json.Node states = n.get("animations");
      states.only(
          "idle",
          "run",
          "jump",
          "fall",
          "guard",
          "deflect",
          "dash",
          "hurt",
          "stagger",
          "death",
          "deathblow");
      for (String key : states.keys()) {
        String id = states.str(key);
        ref(animations, id, states.path + "." + key);
        anim.put(key, id);
      }
      for (String key :
          kind.equals("player")
              ? new String[] {
                "idle",
                "run",
                "jump",
                "fall",
                "guard",
                "deflect",
                "dash",
                "hurt",
                "stagger",
                "death",
                "deathblow"
              }
              : new String[] {"idle", "run", "hurt", "stagger", "death"})
        if (!anim.containsKey(key)) throw states.error("missing state " + key);
      put(
          entities,
          n.str("id"),
          new EntityDef(n, config, anim, defense.blockMultiplier, defense.deflectMultiplier),
          n);
    }
  }
}
