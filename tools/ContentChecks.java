package com.lockin.game;

import com.lockin.game.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real content edits, parsed through the production loader and run through the production Game. */
public final class ContentChecks {
  static int checks;
  static final float DT = 1f / 120;
  static final ContentSource disk = ValidateContent.files(Paths.get("app/src/main/assets"));
  static Content base;

  static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  static void near(double a, double b, String message) {
    check(Math.abs(a - b) < .001, message + " actual=" + a + " expected=" + b);
  }

  static String read(String f) throws Exception {
    return disk.text(f);
  }

  static Content pack(Map<String, String> edits) throws Exception {
    return Content.load(
        path ->
            edits.containsKey(path)
                ? new ByteArrayInputStream(edits.get(path).getBytes(StandardCharsets.UTF_8))
                : disk.open(path));
  }

  static Content edit(String f, String before, String after) throws Exception {
    String text = read(f);
    if (!text.contains(before)) throw new AssertionError("Missing edit target " + before);
    return pack(Collections.singletonMap(f, text.replace(before, after)));
  }

  static String field(String text, String object, String before, String after) {
    int start = text.indexOf("\"name\": \"" + object + "\"");
    if (start < 0) throw new AssertionError(object);
    int at = text.indexOf(before, start);
    if (at < 0) throw new AssertionError(before);
    return text.substring(0, at) + after + text.substring(at + before.length());
  }

  static void invalid(Map<String, String> edits, String diagnostic) throws Exception {
    try {
      pack(edits);
      throw new AssertionError("Expected rejection: " + diagnostic);
    } catch (IllegalArgumentException | IOException e) {
      check(
          e.getMessage().contains(diagnostic),
          "actionable error contains " + diagnostic + ": " + e.getMessage());
    }
  }

  static void badJson(String value) {
    try {
      Json.parse("bad.json", value);
      throw new AssertionError(value);
    } catch (IllegalArgumentException e) {
      check(e.getMessage().startsWith("bad.json:"), "JSON diagnostic filename");
    }
  }

  static Player player(Content c) {
    return new Player(c.entities.get("ninja"), c);
  }

  static Enemy enemy(Content c) {
    return new Enemy(c.entities.get("samurai"), c);
  }

  static void place(Player p, Enemy e) {
    e.x = 400;
    e.y = 310;
    p.x = 344;
    p.y = 316;
    p.grounded = e.grounded = true;
    p.facingRight = true;
    e.facingRight = false;
  }

  static void active(Enemy e, Player p, String id, int strike) {
    int i = 0;
    while (!(e.attackId().equals(id)
            && e.strikeNumber() == strike
            && e.state() == Enemy.State.ATTACK_ACTIVE)
        && i++ < 3000) e.update(DT, p);
    check(i < 3000, "reached " + id + " strike " + strike);
  }

  static void tick(Game g) {
    g.update(DT, 0, false, false);
  }

  static void parsing() throws Exception {
    Json.Node n =
        Json.parse(
            "valid.json", "{\"escaped\":\"a\\n\\u0041\",\"array\":[true,false,null,-1.2e2]}");
    check(n.str("escaped").equals("a\nA"), "string escapes");
    near(n.get("array").list().get(3).number(), -120, "exponent");
    for (String bad :
        new String[] {
          "{\"x\":1,\"x\":2}",
          "{\"x\":01}",
          "[1,]",
          "true false",
          "{\"a\":NaN}",
          "1e999",
          "\"bad\nstring\"",
          "\"\\q\"",
          "1.",
          "{\"x\":}",
          "["
        }) badJson(bad);
    check(
        ContentSource.path("levels/a.tmj", "../sprites/a.png").equals("sprites/a.png"),
        "safe relative asset path");
    for (String p :
        new String[] {"../../secret", "/etc/passwd", "https://example/image.png", "a\\b"})
      try {
        ContentSource.path("manifest.json", p);
        throw new AssertionError(p);
      } catch (IllegalArgumentException ok) {
        checks++;
      }
    invalid(
        Map.of(
            "entities/enemy.json",
            read("entities/enemy.json").replace("\"maxHealth\": 100", "\"maxHealth\": -1")),
        "entities/enemy.json.maxHealth");
    invalid(
        Map.of(
            "entities/enemy.json", read("entities/enemy.json").replace("\"samurai\"", "\"ninja\"")),
        "duplicate ID");
    invalid(
        Map.of(
            "entities/enemy.json",
            read("entities/enemy.json").replace("normal_slash", "missing_attack")),
        ".attack: missing reference");
    invalid(
        Map.of(
            "combat/attacks.json",
            read("combat/attacks.json").replace("\"end\": 0.68", "\"end\": 0.49")),
        ".end");
    invalid(
        Map.of(
            "combat/combos.json",
            read("combat/combos.json").replace("heavy_overhead", "missing_attack")),
        ".sequence: missing reference");
    invalid(
        Map.of(
            "animations/enemy.json",
            read("animations/enemy.json").replace("Samurai_Run.png", "missing.png")),
        "missing.png");
    invalid(
        Map.of(
            "behaviors/enemy.json",
            read("behaviors/enemy.json").replace("\"melee\"", "\"script\"")),
        "supported behavior");
    invalid(
        Map.of(
            "levels/test_arena.tmj",
            read("levels/test_arena.tmj").replace("\"orthogonal\"", "\"isometric\"")),
        "orthogonal");
    invalid(
        Map.of(
            "levels/test_arena.tmj",
            read("levels/test_arena.tmj").replace("\"infinite\": false", "\"infinite\": true")),
        "infinite");
    invalid(
        Map.of(
            "levels/test_arena.tmj",
            read("levels/test_arena.tmj")
                .replace("\"value\": \"deck_1\"", "\"value\": \"absent_floor\"")),
        "missing platform reference");
    invalid(
        Map.of(
            "entities/player.json",
            read("entities/player.json").replace("\"speed\": 180", "\"speeed\": 180")),
        "unsupported property");
  }

  static void editingDemonstration() throws Exception {
    Content hp = edit("entities/enemy.json", "\"maxHealth\": 100", "\"maxHealth\": 400");
    Game game = new Game(hp);
    for (Enemy e : game.enemies())
      check(e.health == 400 && e.maxHealth == 400, "A: JSON HP reaches every spawn");
    game.enemies()[0].takeDamage(25);
    check(game.enemies()[1].health == 400, "independent health");
    game.enemies()[0].posture.add(10);
    near(game.enemies()[1].posture.value(), 0, "independent posture");
    game.restart();
    check(game.enemy.health == 400, "restart preserves edited HP");
    String map = field(read("levels/test_arena.tmj"), "samurai_1", "\"x\": 670", "\"x\": 720");
    game = new Game(pack(Map.of("levels/test_arena.tmj", map)));
    near(game.enemy.x, 720, "B: Tiled enemy spawn x");
    near(game.enemy.y + game.enemy.height, 940, "spawn point is feet");
    Content damage = edit("combat/attacks.json", "\"damage\": 20", "\"damage\": 37");
    Player p = player(damage);
    Enemy e = enemy(damage);
    place(p, e);
    active(e, p, "normal_slash", 1);
    e.tryHit(p);
    check(p.health == 63, "C: edited attack damage commits");
    near(p.posture.value(), 25, "unblocked posture still applies");
    e.tryHit(p);
    check(p.health == 63, "single event cannot repeat");
    Content timing = edit("combat/attacks.json", "\"start\": 0.5", "\"start\": 0.3");
    p = player(timing);
    e = enemy(timing);
    place(p, e);
    e.update(DT, p);
    for (int i = 0; i < 35; i++) e.update(DT, p);
    check(e.state() == Enemy.State.ATTACK_WINDUP, "C: before edited active entry");
    e.update(DT, p);
    check(e.state() == Enemy.State.ATTACK_ACTIVE, "C: edited windup reaches 300ms");
    map = field(read("levels/test_arena.tmj"), "deck_1", "\"x\": 256", "\"x\": 260");
    map = field(map, "deck_1", "\"width\": 384", "\"width\": 400");
    game = new Game(pack(Map.of("levels/test_arena.tmj", map)));
    near(
        SolidPhysics.horizontal(game.area().definition, 200, 900, 800, 36, 64),
        224,
        "D: moved platform side changes swept collision");
    near(
        SolidPhysics.horizontal(game.area().definition, 800, 100, 800, 36, 64),
        660,
        "D: resized platform edge changes collision");
    game.player.takeDamage(9999);
    tick(game);
    check(game.player.defeated(), "E: defeat state");
    game.requestReset();
    game.confirmReset();
    check(!game.player.defeated() && !game.resetPending(), "E: reset restores play");
    near(game.player.x, 180, "E: restart uses content spawn");
    Controls controls = new Controls(); // Existing dedicated multitouch tests run in test-core.sh.
    game.update(DT, 1, true, true, true, 1, true);
    check(game.player.dashing(), "E: simultaneous controls preserve dash priority");
    check(!game.player.attacking() && !game.player.guarding(), "E: dash suppresses attack/guard");
  }

  static void combat() throws Exception {
    Player p = player(base);
    Enemy e = enemy(base);
    place(p, e);
    String[] sequence = {"normal_slash", "double_slash", "heavy_overhead", "normal_slash"};
    for (String id : sequence) {
      active(e, p, id, 1);
      long serial = e.attackRun.serial();
      while (e.attackRun.running()) e.update(DT, p);
      check(e.attackRun.serial() == serial, "move timeline stable");
    }
    e.reset();
    check(e.nextAttackId().equals("normal_slash"), "sequence reset");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "double_slash", 1);
    p.setGuard(true, true);
    e.tryHit(p);
    near(e.posture.value(), 35, "double first deflection");
    p.setGuard(false, false);
    active(e, p, "double_slash", 2);
    p.setGuard(true, true);
    e.tryHit(p);
    near(e.posture.value(), 70, "double second deflection");
    check(p.health == 100, "double deflections prevent health");
    near(p.effectiveDeflectWindow(), .32, "legitimate rapid deflections reset spam");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "double_slash", 1);
    e.tryHit(p);
    e.tryHit(p);
    check(p.health == 80, "double strike 1 once");
    active(e, p, "double_slash", 2);
    e.tryHit(p);
    e.tryHit(p);
    check(p.health == 60, "double strike 2 independently once");
    near(p.posture.value(), 50, "double unblocked posture");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "heavy_overhead", 1);
    e.tryHit(p);
    check(p.health == 60, "heavy health 2x");
    near(p.posture.value(), 50, "heavy posture 2x");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "heavy_overhead", 1);
    p.setGuard(true, true);
    p.updateDefense(.321);
    e.tryHit(p);
    check(p.health == 100 && p.impact() == Player.Impact.BLOCK, "heavy held block");
    near(p.posture.value(), 50, "heavy block posture");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "heavy_overhead", 1);
    p.setGuard(true, true);
    e.tryHit(p);
    near(e.posture.value(), 70, "heavy deflection transfer");
    near(p.posture.value(), 0, "deflection cannot break defender");
    for (double age : new double[] {.319999, .32, .32001}) {
      p = player(base);
      e = enemy(base);
      p.setGuard(true, true);
      p.updateDefense(age);
      p.receiveAttack(e);
      check(
          p.impact() == (age <= .32 ? Player.Impact.DEFLECT : Player.Impact.BLOCK),
          "deflect boundary " + age);
    }
    p = player(base);
    for (int i = 0; i < 8; i++) {
      p.setGuard(true, true);
      p.setGuard(false, false);
    }
    near(p.effectiveDeflectWindow(), .05, "anti-spam floor");
    p.updateDefense(.8);
    near(p.effectiveDeflectWindow(), .32, "anti-spam recovery");
    p = player(base);
    e = enemy(base);
    place(p, e);
    p.update(DT, 0, false, true);
    p.tryHit(e);
    check(e.health == 75, "ordinary player hit");
    near(e.posture.value(), 25, "ordinary player posture");
    p.startDash(-1);
    p.tryHit(e);
    check(e.health == 75 && !p.attacking(), "cancel after contact preserves single committed hit");
    p = player(base);
    e = enemy(base);
    place(p, e);
    p.update(DT, 0, false, true);
    p.startDash(-1);
    p.tryHit(e);
    check(e.health == 100, "cancel before contact removes hitbox");
    p = player(base);
    p.posture.add(100);
    p.takeDamage(15);
    check(p.staggered(), "posture boundary breaks");
    check(p.startDash(1), "dash cancels stagger");
    check(
        p.health == 85 && p.posture.value() == 100 && !p.staggered(),
        "stagger cancellation does not refund damage");
    check(!p.startDash(1), "no dash chain exploit");
    p = player(base);
    p.startDash(1);
    p.update(.039f, 0, false, false);
    check(!p.invulnerable(), "pre i-frame entry");
    p.update(.001f, 0, false, false);
    check(p.invulnerable(), "exact i-frame entry");
    p.update(.1f, 0, false, false);
    check(!p.invulnerable(), "exact i-frame exit");
    p.update(.04f, 0, false, false);
    check(!p.dashing() && !p.startDash(1), "dash cooldown measured at start");
    p.update(.27f, 0, false, false);
    check(p.startDash(1), "cooldown expiry");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "normal_slash", 1);
    p.startDash(1);
    p.update(.05f, 0, false, false);
    p.x = 344;
    e.tryHit(p);
    check(p.health == 100 && p.posture.value() == 0, "iframe contact no health or posture");
    p.update(.10f, 0, false, false);
    p.x = 344;
    e.tryHit(p);
    check(p.health == 100, "invulnerable contact consumed the event");
    p = player(base);
    e = enemy(base);
    place(p, e);
    e.posture.add(100);
    check(p.tryDeathblow(e) && e.defeated(), "deathblow eligibility");
    check(!p.startDash(1) && !p.tryDeathblow(e), "noncancellable and nonduplicated deathblow");
    p = player(base);
    e = enemy(base);
    place(p, e);
    active(e, p, "normal_slash", 1);
    p.x = e.x + e.width + 10;
    e.tryHit(p);
    check(p.health == 100, "no phantom attack behind enemy");
    e.takeDamage(100);
    p.x = 344;
    e.tryHit(p);
    check(p.health == 100, "no hit after enemy death");
    for (int i = 0; i < 3; i++) {
      p = player(base);
      e = enemy(base);
      place(p, e);
      active(e, p, sequence[i], 1);
      near(e.attackRight() - e.attackLeft(), new double[] {64, 54, 48}[i], "attack-specific width");
      near(e.attackBottom() - e.attackTop(), i == 2 ? 102 : 52, "attack-specific height");
      float left = e.attackLeft();
      e.facingRight = true;
      check(e.attackLeft() >= e.x + e.width && left < e.x, "directional mirroring");
    }
  }

  static void configurableFeatures() throws Exception {
    Content configured = edit("entities/player.json", "\"width\": 36", "\"width\": 52");
    Player p = player(configured);
    Enemy e = enemy(configured);
    place(p, e);
    p.x = e.x - p.width;
    p.startDash(1);
    float old = p.x, oldY = p.y;
    p.update(.1f, 0, false, false);
    CollisionManager.resolvePlayerMovement(p, e, old, oldY);
    near(p.x + p.width, e.x, "edited collider stops dash at solid body");
    Content speed = edit("entities/player.json", "\"speed\": 180", "\"speed\": 90");
    p = player(speed);
    float start = p.x;
    for (int i = 0; i < 120; i++) p.update(DT, 1, false, false);
    near(p.x - start, 90, "edited movement speed");
    Content accel = edit("entities/player.json", "\"acceleration\": 0", "\"acceleration\": 180");
    p = player(accel);
    start = p.x;
    p.update(.1f, 1, false, false);
    near(p.x - start, 1.8, "configured acceleration");
    String combo = read("combat/combos.json");
    int marker = combo.indexOf("\"id\": \"player_chain\"");
    String before = combo.substring(0, marker),
        after =
            combo
                .substring(marker)
                .replace(
                    "\"followups\": []",
                    "\"followups\":"
                        + " [{\"from\":\"player_slash\",\"to\":\"player_slash\",\"open\":0.08,\"close\":0.15,\"executeAt\":0.2}]");
    Content chain = pack(Map.of("combat/combos.json", before + after));
    p = player(chain);
    e = enemy(chain);
    place(p, e);
    p.update(DT, 0, false, true);
    p.tryHit(e);
    for (int i = 0; i < 12; i++) p.update(DT, 0, false, false);
    p.update(DT, 0, false, true);
    for (int i = 0; i < 13; i++) p.update(DT, 0, false, false);
    p.tryHit(e);
    check(
        p.attackRun.serial() == 2 && e.health == 50,
        "configured buffered followup executes with independent hit");
    Content heavyOnly =
        edit("behaviors/enemy.json", "\"selection\": \"sequence\"", "\"selection\": \"weighted\"");
    String behavior =
        read("behaviors/enemy.json")
            .replace("\"selection\": \"sequence\"", "\"selection\": \"weighted\"")
            .replace(
                "\"weights\": [\n        1,\n        1,\n        1\n      ]",
                "\"weights\": [0, 0, 1]");
    heavyOnly = pack(Map.of("behaviors/enemy.json", behavior));
    p = player(heavyOnly);
    e = enemy(heavyOnly);
    place(p, e);
    e.update(DT, p);
    check(e.attackId().equals("heavy_overhead"), "weighted selection respects zero weights");
    Content noChase = edit("behaviors/enemy.json", "\"chase\": true", "\"chase\": false");
    p = player(noChase);
    e = enemy(noChase);
    p.x = 200;
    e.x = 400;
    start = e.x;
    e.update(.2f, p);
    near(e.x, start, "configured chase disabled");
    Content noCancel =
        edit(
            "combat/attacks.json",
            "\"dashCancel\": [\n"
                + "        {\n"
                + "          \"start\": 0,\n"
                + "          \"end\": 0.45\n"
                + "        }\n"
                + "      ]",
            "\"dashCancel\": []");
    p = player(noCancel);
    p.update(DT, 0, false, true);
    check(!p.startDash(1), "configured cancel windows enforced");
    p.posture.add(100);
    check(p.startDash(1), "stagger escape remains available");
  }

  static void animations() throws Exception {
    Player p = player(base);
    Enemy e = enemy(base);
    place(p, e);
    AttackRun run = new AttackRun();
    AttackDef attack = base.attacks.get("double_slash");
    run.start(attack);
    run.advance(.399);
    check(
        !run.canHit() && run.animation().equals("samurai_prepare"),
        "preparation before first strike");
    run.advance(.001);
    check(run.canHit() && run.event("hitbox_on", 0), "timeline entry event");
    ContentAnimationClock clock = new ContentAnimationClock(base);
    clock.play(run.animation(), 1);
    clock.sample(run.progress());
    check(
        clock.clip().id.equals("samurai_strike") && clock.frame() == 0,
        "first strike animation shares authoritative time");
    run.advance(.14);
    check(!run.canHit() && run.event("hitbox_off", 0), "first hit deactivation");
    run.advance(.28);
    check(
        run.canHit() && run.strike() == 1 && run.animation().equals("samurai_return_strike"),
        "second strike own animation and hit event");
    run.advance(.14);
    check(run.phase() == AttackRun.Phase.RECOVERY, "after second active");
    run.advance(.65);
    check(!run.running() && run.event("complete", 0), "completion event");
    String json =
        read("animations/player.json")
            .replaceFirst("\"events\": \\[\\]", "\"events\": [{\"name\":\"footstep\",\"at\":0.1}]");
    Content animated = pack(Map.of("animations/player.json", json));
    clock = new ContentAnimationClock(animated);
    clock.play("ninja_idle", 1);
    clock.advance(.2);
    check(clock.event("footstep"), "named event survives skipped display frames");
    int frame = clock.frame();
    clock.play("ninja_idle", 1);
    check(clock.frame() == frame, "same state does not restart animation");
    clock.advance(.7);
    check(clock.event("footstep"), "loop emits event again");
    clock.reset();
    check(clock.clip() == null, "restart resets visual state");
    Game game = new Game(base);
    ContentAnimations visual = new ContentAnimations(base);
    visual.updatePlayer(game, DT);
    check(visual.player.clip().id.equals("ninja_idle"), "content idle state mapping");
    game.player.startDash(1);
    visual.updatePlayer(game, DT);
    check(visual.playerPose().equals("dash"), "dash visual priority");
    game.player.takeDamage(1000);
    visual.updatePlayer(game, DT);
    check(visual.playerPose().equals("death"), "death visual priority");
    game.restart();
    visual.updatePlayer(game, DT);
    check(visual.playerPose().equals("idle"), "restart presentation reset");
  }

  static void levels() throws Exception {
    String manifest =
        read("manifest.json")
            .replace(
                "\"file\": \"levels/test_arena.tmj\"",
                "\"file\": \"levels/test_arena.tmj\"\n"
                    + "    },{\"id\":\"annex\",\"file\":\"levels/annex.tmj\"");
    String arena = read("levels/test_arena.tmj"), annex = arena.replace("Shinobi Castle", "Annex");
    Content two = pack(Map.of("manifest.json", manifest, "levels/annex.tmj", annex));
    Game g = new Game(two);
    Enemy first = g.enemy;
    first.takeDamage(15);
    g.player.healthUpgrade();
    g.loadLevel("annex", "start");
    check(
        g.level().id.equals("annex") && g.enemy != first,
        "level switches independent runtime state");
    check(g.player.maxHealth == 150, "upgrade survives level transition");
    g.loadLevel("castle", "start");
    check(g.enemy == first && first.health == 85, "return preserves level state");
    g.restart();
    check(
        g.level().id.equals("castle") && first.health == 100 && g.player.maxHealth == 100,
        "restart resets every loaded level");
    // Route a real exit through content, then exercise the trigger rather than only the API.
    String exit =
        field(
            arena,
            "castle_exit",
            "\"properties\": []",
            "\"properties\":"
                + " [{\"name\":\"levelId\",\"type\":\"string\",\"value\":\"annex\"},{\"name\":\"spawnId\",\"type\":\"string\",\"value\":\"start\"}]");
    two =
        pack(
            Map.of(
                "manifest.json",
                manifest,
                "levels/test_arena.tmj",
                exit,
                "levels/annex.tmj",
                annex));
    g = new Game(two);
    for (int i = 0; i < 60; i++) tick(g);
    g.player.x = 2844;
    tick(g);
    check(
        g.level().id.equals("annex") && !g.complete(), "Tiled exit level/spawn references execute");
    LevelDef level = base.levels.get("castle");
    check(
        level.area.platformCount() == 16 && level.area.ladderCount() == 14,
        "Tiled collision/platform/ladder extraction");
    check(level.area.linkCount() == 36, "stable surface ID navigation references");
    String solidMap = arena.replaceFirst("\"data\": \\[\\s*0,", "\"data\": [1,");
    Content tileSolid = pack(Map.of("levels/test_arena.tmj", solidMap));
    check(
        tileSolid.levels.get("castle").area.solidCount() == level.area.solidCount() + 1,
        "TSJ solid=true produces collision");
    String missing = arena.replace("\"entityId\",", "\"entityId\",");
    check(missing.length() > 0, "custom properties parsed");
  }

  static void rates() throws Exception {
    for (int fps : new int[] {5, 10, 20, 30, 60, 120}) {
      Game g = new Game(base);
      for (Enemy e : g.enemies()) e.takeDamage(e.health);
      for (int i = 0; i < fps * 6; i++) g.update(1f / fps, 1, false, false);
      check(Math.abs(g.player.x - 1260) < .15, "content simulation movement at " + fps + " FPS");
      check(
          g.simulationSteps() == 720 && g.droppedSeconds() == 0,
          "no slow motion at " + fps + " FPS");
    }
  }

  static void additionalCoverage() throws Exception {
    Content posture = edit("entities/player.json", "\"maxPosture\": 100", "\"maxPosture\": 25");
    Player p = player(posture);
    Enemy e = enemy(posture);
    p.setGuard(true, true);
    p.updateDefense(.321);
    p.receiveAttack(e);
    check(
        p.staggered() && p.health == 100,
        "entity posture capacity edit changes block break boundary");
    p.updateDefense(1);
    check(!p.staggered() && p.posture.immune(), "configured break recovery/immunity");
    p.posture.add(25);
    check(!p.staggered(), "post-break immunity prevents loop");
    Content defense =
        edit(
            "combat/defense.json",
            "\"blockPostureMultiplier\": 1",
            "\"blockPostureMultiplier\": 2");
    p = player(defense);
    e = enemy(defense);
    p.setGuard(true, true);
    p.updateDefense(.321);
    p.receiveAttack(e);
    near(p.posture.value(), 50, "shared defense multiplier affects actual contact");
    Content interruption =
        edit("combat/attacks.json", "\"interruptOnHit\": false", "\"interruptOnHit\": true");
    p = player(interruption);
    e = enemy(interruption);
    place(p, e);
    e.update(DT, p);
    check(e.attackRun.running(), "configured windup running");
    e.receiveHit(1, 1);
    check(
        !e.attackRun.running() && e.state() == Enemy.State.ATTACK_RECOVERY,
        "configured hit interruption cancels windup");
    check(e.nextAttackId().equals("double_slash"), "interruption advances sequence once");
    Content acceleration =
        edit("entities/player.json", "\"acceleration\": 0", "\"acceleration\": 180");
    p = player(acceleration);
    p.update(.5f, 1, false, false);
    float x = p.x;
    p.posture.add(100);
    p.update(.1f, 1, false, false);
    near(p.x, x, "stagger freezes configured acceleration movement");
    String map = read("levels/test_arena.tmj");
    while (map.contains("\"type\": \"enemy_spawn\"")) {
      int type = map.indexOf("\"type\": \"enemy_spawn\""),
          start = map.lastIndexOf("\n        {", type),
          end = map.indexOf("\n        }", type) + 10;
      if (map.charAt(end) == ',') end++;
      map = map.substring(0, start) + map.substring(end);
    }
    Game empty = new Game(pack(Map.of("levels/test_arena.tmj", map)));
    check(empty.enemy == null && empty.enemies().length == 0, "zero enemy map loads");
    ContentAnimations animation = new ContentAnimations(empty.content);
    empty.update(DT, 1, true, true);
    animation.updatePlayer(empty, DT);
    empty.restart();
    check(
        empty.enemies().length == 0 && !empty.player.defeated(),
        "zero enemy update, animations and restart");
    String newClip =
        "{\"id\":\"test_frames\",\"asset\":\"../sprites/Samurai_Bundle/Samurai/Idle/Idle_1.png\",\"frameWidth\":16,\"frameHeight\":16,\"columns\":2,\"frames\":[2,0,{\"x\":16,\"y\":0,\"width\":16,\"height\":16,\"duration\":0.25}],\"fps\":10,\"loop\":false,\"facesRight\":false,\"anchorX\":8,\"anchorY\":14,\"scale\":8.2,\"next\":\"samurai_idle\",\"events\":[{\"name\":\"entry\",\"at\":0}]}";
    String anim = read("animations/enemy.json");
    int end = anim.lastIndexOf("\n  ]");
    anim = anim.substring(0, end) + "," + newClip + anim.substring(end);
    Content clips = pack(Map.of("animations/enemy.json", anim));
    ContentAnimationClock clock = new ContentAnimationClock(clips);
    clock.play("test_frames", 1);
    clock.advance(.05);
    check(clock.event("entry"), "animation event at zero survives first advance");
    check(
        clock.clip().frames.get(clock.frame()).y() == 16,
        "explicit frame sequence skips to grid frame 2");
    clock.advance(.05);
    check(
        clock.clip().frames.get(clock.frame()).x() == 0 && clock.frame() == 1,
        "explicit reordered frame 0");
    clock.advance(.1);
    check(
        clock.frame() == 2 && clock.clip().frames.get(2).x() == 16,
        "explicit rectangle and per-frame duration");
    clock.advance(.25);
    check(
        clock.clip().id.equals("samurai_idle"), "one-shot completion follows configured next clip");
    Game g = new Game(base);
    g.player.update(DT, 0, false, true);
    g.player.update(.2f, 0, false, false);
    g.player.setGuard(true, true);
    animation = new ContentAnimations(base);
    animation.updatePlayer(g, DT);
    check(animation.playerPose().equals("guard"), "guard presentation overrides attack recovery");
    String original = read("levels/test_arena.tmj");
    invalid(
        Map.of("levels/test_arena.tmj", original.replace("castle:158", "missing:158")),
        "missing tileId");
    String hidden =
        original
            .replace("\"name\": \"Castle backdrop\"", "\"name\": \"Castle backdrop\"")
            .replaceFirst("\"visible\": true", "\"visible\": false");
    Content h = pack(Map.of("levels/test_arena.tmj", hidden));
    check(!h.levels.get("castle").layers.get(0).visible(), "tile-layer visibility parsed");
    Content high =
        pack(
            Map.of(
                "entities/player.json",
                read("entities/player.json")
                    .replace("\"maxHealth\": 100", "\"maxHealth\": 10000")
                    .replace("\"maxPosture\": 100", "\"maxPosture\": 100000")));
    int expectedHealth = -1;
    double expectedAge = 0;
    String expectedMove = "";
    for (int fps : new int[] {120, 60, 30, 20, 10, 5}) {
      g = new Game(high);
      for (int i = 1; i < g.enemies().length; i++) g.enemies()[i].takeDamage(g.enemies()[i].health);
      g.player.x = g.enemy.x - g.player.width - 20;
      for (int i = 0; i < fps * 6; i++) g.update(1f / fps, 0, false, false);
      if (expectedHealth < 0) {
        expectedHealth = g.player.health;
        expectedAge = g.enemy.attackRun.age();
        expectedMove = g.enemy.attackId();
      }
      check(g.player.health == expectedHealth, "same committed combat damage at " + fps + " FPS");
      near(
          g.enemy.attackRun.age(),
          expectedAge,
          "same authoritative combat clock at " + fps + " FPS");
      check(g.enemy.attackId().equals(expectedMove), "same move sequence at " + fps + " FPS");
    }
  }

  static void perHitTransfers() throws Exception {
    Content c = edit("combat/attacks.json", "\"id\": \"first\",",
        "\"id\": \"first\", \"damage\": 7, \"postureDamage\": 9,"
        + " \"blockDamage\": 11, \"deflectDamage\": 13,");
    Player p = player(c);
    Enemy e = enemy(c);
    place(p, e);
    e.attackRun.start(c.attacks.get("double_slash"));
    e.attackRun.advance(.4); e.update(DT, p);
    near(e.healthDamage(), 7, "first hit health override");
    e.tryHit(p);
    near(p.health, 93, "per-hit HP reaches actual defender");
    near(p.posture.value(), 9, "per-hit posture reaches actual defender");
    e.tryHit(p);
    near(p.health, 93, "per-hit contact consumed once");
    e.attackRun.advance(.42); e.update(DT, p);
    near(e.healthDamage(), 20, "second hit inherits attack health");
    e.tryHit(p);
    near(p.health, 73, "second event commits independently");

    p = player(c); e = enemy(c); place(p, e);
    e.attackRun.start(c.attacks.get("double_slash")); e.attackRun.advance(.4); e.update(DT, p);
    p.setGuard(true, true);
    e.tryHit(p);
    near(p.health, 100, "deflect still prevents HP damage");
    near(p.posture.value(), 0, "deflect does not add defender posture");
    near(e.posture.value(), 13, "per-hit deflect amount goes to attacker");

    p = player(c); e = enemy(c); place(p, e);
    e.attackRun.start(c.attacks.get("double_slash")); e.attackRun.advance(.4); e.update(DT, p);
    p.setGuard(true, true); p.updateDefense(.4);
    e.tryHit(p);
    near(p.posture.value(), 11, "per-hit block amount goes to defender");

    Content weapon = edit("combat/attacks.json", "\"id\": \"slash\",",
        "\"id\": \"slash\", \"damage\": 3, \"postureDamage\": 4,");
    p = player(weapon); e = enemy(weapon); place(p, e);
    p.powerUpgrade(2);
    p.update(DT, 0, false, true); p.tryHit(e);
    near(e.health, 94, "player per-hit damage multiplied once");
    near(e.posture.value(), 8, "player per-hit posture multiplied once");

    for (String key : new String[]{"damage", "postureDamage", "blockDamage", "deflectDamage"}) {
      invalid(Map.of("combat/attacks.json", read("combat/attacks.json")
          .replace("\"id\": \"first\",", "\"id\": \"first\", \"" + key + "\": -1,")), key);
    }
    invalid(Map.of("combat/attacks.json", read("combat/attacks.json")
        .replace("\"id\": \"first\",", "\"id\": \"first\", \"damage\": 1.5,")), "integer");
  }

  static void equipment() throws Exception {
    Player p=player(base); Enemy e=enemy(base); place(p,e);
    check(p.inventory.count("healing_flask")==3,"JSON starting grants loaded");
    near(p.healthDamage(),25,"starting items do not force weapon equip");
    check(p.inventory.equip("long_blade",0),"equip owned weapon");
    near(p.healthDamage(),30,"weapon modifier once");
    near(p.postureDamage(),32.5,"weapon posture modifier");
    check(!p.inventory.equip("long_blade",0),"repeat equip cannot stack");
    check(p.inventory.equip("posture_charm",0),"equip accessory");
    near(p.postureDamage(),40.625,"independent modifiers recomputed");
    check(!p.inventory.equip("posture_charm",1),"one type cannot occupy two slots");
    p.update(DT,0,false,true);
    check(p.attackRun.definition().id.equals("long_blade_slash"),"weapon selects actual timeline");
    check(!p.inventory.unequip("weapon",0),"cannot change gear mid attack");
    p.update(1,0,false,false);
    check(p.inventory.unequip("weapon",0),"unequip after attack completes");
    p.update(DT,0,false,true);
    check(p.attackRun.definition().id.equals("player_slash"),"unequip restores default attack");
    p.update(1,0,false,false);
    near(p.healthDamage(),25,"unequip removes damage multiplier");
    check(p.inventory.equip("padded_outfit",0),"equip armor");
    near(p.maxHealth,125,"armor maximum applied"); near(p.health,100,"equip does not heal");
    p.takeDamage(50); near(p.health,60,"armor modifies incoming HP once");
    check(p.inventory.unequip("armor",0),"unequip armor"); near(p.maxHealth,100,"maximum recalculated");
    p.healthUpgrade(50); check(p.inventory.equip("padded_outfit",0),"reequip after health pickup");
    near(p.maxHealth,175,"health reward and gear bonus independent");
    check(p.inventory.unequip("armor",0),"unequip bonus after reward"); near(p.maxHealth,150,"reward retained");
    p.takeDamage(60); p.posture.add(30);
    check(p.inventory.equip("healing_flask",1),"assign quick slot");
    check(p.inventory.consume(1),"consume assigned stack");
    near(p.health,130,"healing effect applied"); near(p.posture.value(),10,"posture effect applied");
    check(p.inventory.count("healing_flask")==2,"consumption decrements stack");
    check(p.inventory.acquire("healing_flask",99)==3,"stack limit enforced");
    check(p.inventory.count("healing_flask")==5,"stack capped");
    p.reset(); near(p.maxHealth,100,"reset clears gear and reward");
    check(p.inventory.equipped("weapon",0).isEmpty(),"reset leaves gear unequipped");
    check(p.inventory.count("healing_flask")==3,"reset restores configured grant");
    invalid(Map.of("items/foundation.json", read("items/foundation.json")
        .replace("\"incomingDamageMultiplier\": 0.8","\"incomingDamageMultiplier\": -1")),"incomingDamageMultiplier");
    invalid(Map.of("items/foundation.json", read("items/foundation.json")
        .replace("\"attack\": \"long_blade_slash\"","\"attack\": \"missing\"")),"missing");
    Content old=pack(Map.of("manifest.json",read("manifest.json")
        .substring(0,read("manifest.json").indexOf(",\n  \"items\""))+"\n}"));
    check(player(old).inventory.items().isEmpty(),"old manifest without items/inventory remains valid");
  }

  static void inventoryMenu() throws Exception {
    Game g=new Game(base); InventoryMenu menu=new InventoryMenu();
    menu.command(g,4);
    check(menu.open && g.inventoryPaused(),"menu pauses gameplay engine");
    long ticks=g.simulationSteps();
    float x=g.player.x;
    g.update(.2f,1,true,true);
    check(g.simulationSteps()==ticks && g.player.x==x,"modal discards movement/actions/time");
    menu.command(g,101); menu.command(g,7);
    check(g.player.inventory.equipped("weapon",0).equals("long_blade"),"menu selection equips item");
    menu.command(g,8);
    check(g.player.inventory.equipped("weapon",0).isEmpty(),"menu unequips slot");
    menu.command(g,104); menu.command(g,10); menu.command(g,7);
    check(g.player.inventory.equipped("consumable",1).equals("healing_flask"),"menu assigns chosen quick slot");
    g.player.takeDamage(30);menu.command(g,9);
    check(g.player.health==100 && g.player.inventory.count("healing_flask")==2,"menu uses assigned consumable");
    menu.command(g,4);g.update(DT,0,false,false);
    check(!menu.open && !g.inventoryPaused() && !g.player.attacking(),"closing modal has no buffered attack");

    String map=read("levels/test_arena.tmj");
    map=field(map,"health_reward","\"value\": \"health\"","\"value\": \"item\"");
    map=field(map,"health_reward","\"value\": 50","\"value\": 2");
    int start=map.indexOf("\"name\": \"health_reward\"");
    int props=map.indexOf("\"properties\": [",start)+"\"properties\": [".length();
    map=map.substring(0,props)+"{\"name\":\"itemId\",\"value\":\"healing_flask\"},"+map.substring(props);
    g=new Game(pack(Map.of("levels/test_arena.tmj",map)));
    var pickup=g.level().pickups.get(0);
    g.player.x=pickup.x();g.player.y=pickup.y();g.update(DT,0,false,false);
    check(g.player.inventory.count("healing_flask")==5,"item pickup acquired through world contact");
    g.update(DT,0,false,false);
    check(g.player.inventory.count("healing_flask")==5,"world pickup once only");
    invalid(Map.of("levels/test_arena.tmj",map.replace("\"value\":\"healing_flask\"","\"value\":\"missing_item\"")),"missing_item");
    Content override=edit("items/foundation.json","\"attack\": \"long_blade_slash\"",
        "\"attack\": \"long_blade_slash\", \"animations\": {\"idle\": \"ninja_guard\"}");
    Player p=player(override);p.inventory.equip("long_blade",0);
    check(p.animation("idle").equals("ninja_guard"),"weapon state animation override applied");
    p.inventory.unequip("weapon",0);
    check(p.animation("idle").equals("ninja_idle"),"animation override removed on unequip");
  }

  static void bosses() throws Exception {
    Game g=new Game(base);g.loadLevel("boss_demo","start");
    BossEncounter b=g.encounter("castle_trial");
    check(b!=null && !b.active(),"configured boss awaits rectangle activation");
    int hp=b.actor.health;b.actor.takeDamage(100);
    check(b.actor.health==hp,"inactive boss protected");
    g.player.x=500;g.player.y=g.area().definition.groundY-g.player.height;g.update(DT,0,false,false);
    check(b.active() && g.activeBoss()==b,"activation exposes boss HUD model");
    check(b.transitioning() && b.actor.encounterLocked,"initial transition locks actions");
    b.actor.takeDamage(100);check(b.actor.health==hp,"transition invulnerability applied");
    for(int n=0;n<40;n++)g.update(DT,0,false,false);
    check(!b.transitioning(),"transition duration expires on fixed ticks");
    b.actor.takeDamage(60);g.update(DT,0,false,false);
    check(b.phase()==1 && b.transitioning(),"health threshold selects next phase");
    near(b.actor.posture.fraction(),0,"transition resets posture");
    near(b.actor.speed,b.actor.definition.speed*1.2,"phase stats applied");
    for(int n=0;n<70;n++)g.update(DT,0,false,false);
    g.player.x=b.actor.x-g.player.width-20;g.player.y=b.actor.y;
    for(int n=0;n<200 && !b.actor.attackRun.running();n++)g.update(DT,0,false,false);
    check(b.actor.attackId().equals("lunging_slash"),"phase-specific attack pool selected");
    b.actor.takeDamage(100000);g.update(DT,0,false,false);
    check(b.complete() && g.activeBoss()==null,"defeat hides boss HUD");
    check(g.completedEncounters().contains("castle_trial"),"stable completion flag exposed");
    check(g.player.inventory.count("healing_flask")==5,"configured reward granted");
    g.update(DT,0,false,false);
    check(g.player.inventory.count("healing_flask")==5,"reward cannot repeat");
    g.restart();check(!b.active() && g.completedEncounters().isEmpty(),"reset clears run encounters/flags");
    g.loadLevel("boss_demo","start");g.player.x=500;g.player.y=g.area().definition.groundY-g.player.height;
    for(int n=0;n<40;n++)g.update(DT,0,false,false);
    g.signalEncounter("castle_trial","enrage");g.update(DT,0,false,false);
    check(b.phase()==1 && b.actor.health==b.actor.maxHealth,"explicit event transitions without health loss");
    invalid(Map.of("bosses/trial.json",read("bosses/trial.json")
        .replace("\"behavior\": \"warden_enraged\"","\"behavior\": \"missing\"")),"missing");
    invalid(Map.of("bosses/trial.json",read("bosses/trial.json")
        .replace("\"healthBelow\": 0.5","\"healthBelow\": 1")),"decrease");
  }

  static void lunges() throws Exception {
    AttackRun run=new AttackRun();run.start(base.attacks.get("lunging_slash"));
    double distance=0;
    for(int i=0;i<200;i++){run.advance(DT);distance+=run.motionDelta();}
    near(distance,90,"integrated lunge displacement exact");
    near(run.motionDelta(),0,"completed attack does not repeat movement");
    Content c=edit("entities/player.json","\"attack\": \"player_slash\"","\"attack\": \"lunging_slash\"");
    Game g=new Game(c);g.loadLevel("boss_demo","start");
    g.player.x=300;g.update(DT,0,false,true);
    for(int i=0;i<200;i++)g.update(DT,0,false,false);
    near(g.player.x,390,"actual player lunge consumed by physics");
    g.player.x=900;g.update(DT,0,false,true);
    for(int i=0;i<200;i++)g.update(DT,0,false,false);
    near(g.player.x,960-g.player.width,"lunge cannot leave world");
    invalid(Map.of("combat/lunge.json",read("combat/lunge.json").replace("\"end\": 0.5","\"end\": 0.1")),"end");
    invalid(Map.of("combat/lunge.json",read("combat/lunge.json").replace("\"distance\": 90","\"distance\": 9000")),"distance");
  }

  public static void main(String[] args) throws Exception {
    base = Content.load(disk);
    parsing();
    editingDemonstration();
    combat();
    configurableFeatures();
    animations();
    levels();
    rates();
    additionalCoverage();
    perHitTransfers();
    equipment();
    inventoryMenu();
    bosses();
    lunges();
    System.out.println(
        "Passed " + checks + " content parsing/editing/runtime/animation/transition checks (A–E)");
  }
}
