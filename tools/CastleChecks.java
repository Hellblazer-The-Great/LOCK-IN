package com.lockin.game;

/** Production continuous world, solid aperture physics, rewards, traffic and camera. */
public final class CastleChecks {
  static final float DT = 1f / 120;
  static int checks;
  static boolean contentMode;

  static Game fresh() {
    return contentMode ? new Game(FixtureContent.get()) : new Game();
  }

  static void check(boolean yes, String text) {
    checks++;
    if (!yes) throw new AssertionError(text);
  }

  static void near(float a, float b, String text) {
    check(Math.abs(a - b) < .15f, text + " (" + a + ", " + b + ")");
  }

  static void tick(Game g, int move, int vertical, boolean jump, boolean attack) {
    Controls.Snapshot s = new Controls.Snapshot();
    s.move = move;
    s.vertical = vertical;
    s.jump = jump;
    s.attack = attack;
    g.update(DT, s);
  }

  static void step(Game g, int ticks, int move, int vertical) {
    for (int i = 0; i < ticks; i++) tick(g, move, vertical, false, false);
  }

  static Game geometry() {
    Game g = fresh();
    for (Enemy e : g.enemies()) {
      e.detectionRadius = 0;
      e.takeDamage(e.health);
    }
    return g;
  }

  static void walk(Game g, float center) {
    int i = 0;
    while (Math.abs(g.player.x + 18 - center) > 1.7f && i++ < 3000)
      tick(g, g.player.x + 18 < center ? 1 : -1, 0, false, false);
    check(i < 3000, "walk reaches " + center + " feet=" + (g.player.y + 64));
  }

  static void climb(Game g, int ladder, int side) {
    var l = g.area().definition.ladder(ladder);
    walk(g, l.x());
    int i = 0;
    float top = g.area().definition.platform(l.top()).top();
    while ((Math.abs(g.player.y + 64 - top) > .1 || !g.player.climbing) && i++ < 1800)
      tick(g, 0, -1, false, false);
    near(g.player.y + 64, top, "climb reaches top " + ladder);
    check(i < 1800, "climb completes " + ladder);
    walk(g, l.x() + side * 82);
    check(!g.player.climbing && g.player.grounded, "pull over ladder lip " + ladder);
  }

  static void descend(Game g, int ladder) {
    var l = g.area().definition.ladder(ladder);
    float center = g.player.x + 18;
    walk(g, l.x() + (center < l.x() ? -55 : 55));
    float bottom = g.area().definition.platform(l.bottom()).top();
    int count = 0;
    do {
      tick(g, 0, 1, false, false);
    } while ((g.player.climbing || Math.abs(g.player.y + 64 - bottom) > .1) && count++ < 1800);
    near(g.player.y + 64, bottom, "controlled descent completes " + ladder);
    check(!g.player.climbing && g.player.grounded, "descent returns to standing " + ladder);
  }

  static void traversal() {
    Game g = geometry();
    check(g.enemies().length == 5, "five global independent enemies");
    Enemy original = g.enemies()[0];
    float before = 0;
    boolean continuous = true;
    for (int i = 0; i < 1100; i++) {
      before = g.player.x;
      tick(g, 1, 0, false, false);
      continuous &= g.player.x - before <= Player.SPEED * DT + .01;
    }
    check(continuous, "region boundary never teleports");
    check(
        g.player.x > 1800 && g.regionName().equals("Upper Courtyard"),
        "continuous traversal reaches courtyard");
    near(g.player.y + 64, 940, "lower route stays grounded");
    walk(g, 300);
    check(g.regionName().equals("Training Grounds"), "continuous backtracking");
    check(
        g.enemies()[0] == original && original.defeated(),
        "backtracking preserves enemy identity and defeat");
    climb(g, 0, 1);
    climb(g, 1, -1);
    climb(g, 2, 1);
    climb(g, 3, -1);
    walk(g, WorldDefinition.HP_X);
    check(
        g.hpCollected() && g.player.maxHealth == 150 && g.player.health == 150,
        "reachable training reward grants max HP and full heal");
    check(g.notification().contains("50"), "HP notification");
    g.player.takeDamage(10);
    step(g, 500, 0, 0);
    check(g.player.maxHealth == 150 && g.player.health == 140, "reward cannot stack or heal twice");
    walk(g, 400);
    walk(g, 300);
    check(g.player.health == 140, "backtracking cannot recollect HP");
    descend(g, 3);
    descend(g, 2);
    descend(g, 1);
    descend(g, 0);
    walk(g, 1160);
    check(g.hpCollected() && g.player.maxHealth == 150, "HP persists across continuous regions");
    climb(g, 4, 1);
    climb(g, 5, 1);
    climb(g, 6, -1);
    climb(g, 7, 1);
    walk(g, WorldDefinition.POWER_X);
    check(
        g.powerCollected() && g.player.attackMultiplier() == 2,
        "reachable upper courtyard attack reward");
    check(
        g.player.healthDamage() == 50 && g.player.postureDamage() == 50,
        "power scales health and posture once");
    step(g, 500, 0, 0);
    check(g.player.attackMultiplier() == 2, "power cannot stack");
    descend(g, 7);
    descend(g, 6);
    descend(g, 5);
    descend(g, 4);
    near(g.player.y + 64, 940, "full rewarded route returns to lower world");
    g.requestReset();
    g.confirmReset();
    check(
        !g.hpCollected()
            && !g.powerCollected()
            && g.player.maxHealth == 100
            && g.player.attackMultiplier() == 1,
        "reset clears both upgrades");
    check(g.notification().isEmpty(), "reset clears notification");
    near(g.player.x, 180, "reset world spawn");
    near(g.player.y + 64, 940, "reset floor");
    for (Enemy e : g.enemies()) check(!e.defeated(), "reset restores every enemy");
    for (Enemy e : g.enemies()) e.takeDamage(e.health);
    step(g, 2000, 1, 0);
    check(g.complete(), "continuous far-right completion");
    g.requestReset();
    g.confirmReset();
    check(!g.complete(), "completion reset");
  }

  static void solids() {
    Game g = geometry();
    var a = g.area().definition;
    g.player.x = 300;
    g.player.y = 940 - 64;
    tick(g, 0, 0, true, false);
    step(g, 60, 0, 0);
    check(g.player.y >= 844 - .01, "solid underside stops upward jump");
    float y = SolidPhysics.vertical(a, 300, 900, 0, 36, 64);
    near(y, 844, "fast upward sweep hits nearest ceiling");
    y = SolidPhysics.vertical(a, 300, 100, 1000, 36, 64);
    near(y, 220, "fall sweeps nearest roof");
    near(
        SolidPhysics.horizontal(a, 200, 900, 800, 36, 64),
        220,
        "solid side blocks fast right motion");
    near(
        SolidPhysics.horizontal(a, 800, 100, 800, 36, 64),
        640,
        "solid side blocks fast left motion");
    check(
        WorldDefinition.LADDER_WIDTH >= 75 && WorldDefinition.OPENING_WIDTH > Enemy.WIDTH,
        "functional ladder width and body clearance");
    near(
        SolidPhysics.vertical(a, 440 - 18, 900, 750, 36, 64),
        750,
        "ladder aperture permits upward passage");
    near(
        SolidPhysics.vertical(a, 440 - 18, 700, 850, 36, 64),
        850,
        "ladder aperture permits downward passage");
    g.player.x = 300;
    g.player.y = 812 - 64;
    g.player.velocityY = 0;
    g.player.grounded = true;
    step(g, 120, 0, 1);
    near(g.player.y + 64, 812, "DOWN cannot drop through solid floor");
    check(!g.player.climbing, "ordinary floor does not behave like ladder");
    g.player.x = 200;
    g.player.y = 800;
    g.player.velocityY = 0;
    g.player.grounded = false;
    g.player.startDash(1);
    step(g, 20, 0, 0);
    check(g.player.x + 36 <= 256.01f, "actual fast dash cannot tunnel through a floor side");
  }

  static void camera() {
    Game g = geometry();
    Camera c = new Camera();
    c.reset(g.player, g.area().definition);
    near(c.x(), 0, "camera initial left clamp");
    near(c.y(), g.area().definition.height - 540, "camera initial bottom clamp");
    float px = g.player.x, py = g.player.y;
    g.player.x = 1500;
    g.player.y = 200;
    float old = c.x();
    c.update(g.player, g.area().definition, DT);
    check(c.x() > old && c.x() < 500, "smooth horizontal follow");
    check(c.y() < g.area().definition.height - 540 && c.y() > 0, "smooth vertical follow");
    for (int i = 0; i < 400; i++) c.update(g.player, g.area().definition, DT);
    check(
        c.visible(g.player.x, g.player.y, g.player.x + 36, g.player.y + 64),
        "player visible at upper elevation");
    g.player.x = 5000;
    g.player.y = -100;
    for (int i = 0; i < 500; i++) c.update(g.player, g.area().definition, DT);
    near(c.x(), 1920, "camera right world bound");
    near(c.y(), 0, "camera top world bound");
    g.player.x = px;
    g.player.y = py;
    float far = c.x();
    c.update(g.player, g.area().definition, DT);
    check(c.x() < far && c.x() > 0, "smooth horizontal backtracking");
    for (int i = 0; i < 600; i++) c.update(g.player, g.area().definition, DT);
    near(c.x(), 0, "backtracking left clamp");
    near(c.y(), g.area().definition.height - 540, "backtracking bottom clamp");
    c.reset(g.player, g.area().definition);
    near(c.x(), 0, "reset camera x");
    near(c.y(), g.area().definition.height - 540, "reset camera y");
    near(g.player.x, px, "camera never mutates player x");
    near(g.player.y, py, "camera never mutates player y");
  }

  static void enemies() {
    Game g = new Game(CombatConfig.DEFAULT, 10000, 100);
    for (Enemy e : g.enemies()) e.takeDamage(e.health);
    Enemy descending = g.enemies()[0], standing = g.enemies()[1];
    descending.reset();
    standing.reset();
    descending.x = 440 - 22;
    descending.y = 850 - 70;
    descending.climbing = true;
    descending.grounded = false;
    descending.detectionRadius = 1000;
    standing.x = 440 - 22;
    standing.y = 940 - 70;
    standing.grounded = true;
    standing.detectionRadius = 1000;
    g.player.x = 700;
    g.player.y = 876;
    float initial = standing.x;
    boolean separate = true;
    for (int i = 0; i < 700; i++) {
      tick(g, 0, 0, false, false);
      separate &=
          !CollisionManager.overlaps(
              descending.x,
              descending.y,
              descending.x + 44,
              descending.y + 70,
              standing.x,
              standing.y,
              standing.x + 44,
              standing.y + 70);
    }
    check(separate, "two-enemy descending ladder scenario never overlaps");
    check(Math.abs(standing.x - initial) > 45, "standing enemy yields and remains mobile");
    near(descending.y + 70, 940, "descending enemy completes occupied exit");
    check(
        descending.state() != Enemy.State.IDLE && standing.state() != Enemy.State.IDLE,
        "both enemies resume responsive combat/AI");
    g = new Game(CombatConfig.DEFAULT, 10000, 100);
    for (Enemy e : g.enemies()) e.takeDamage(e.health);
    Enemy e = g.enemies()[0];
    e.reset();
    e.x = 670;
    e.y = 870;
    e.detectionRadius = 2000;
    g.player.x = 300;
    g.player.y = 284 - 64;
    g.player.grounded = true;
    step(g, 5000, 0, 0);
    near(e.y + 70, 284, "enemy follows four reachable elevations");
    g.player.x = 900;
    g.player.y = 940 - 64;
    g.player.velocityY = 0;
    step(g, 5000, 0, 0);
    near(e.y + 70, 940, "enemy descends full tower and returns to ground");
  }

  static void allElevations() {
    var a = WorldDefinition.WORLD;
    for (int node = 1; node < a.platformCount(); node++) {
      var deck = a.platform(node);
      float target = -1;
      for (int i = 0; i < a.solidCount(); i++) {
        var solid = a.solid(i);
        if (solid.top() == deck.top()
            && solid.left() >= deck.left()
            && solid.right() <= deck.right()
            && solid.right() - solid.left() >= 100) {
          target = (solid.left() + solid.right()) / 2;
          break;
        }
      }
      check(target >= 0, "authored floor has a clear usable standing section " + node);
      Game g = new Game(CombatConfig.DEFAULT, 100000, 100);
      for (Enemy e : g.enemies()) e.takeDamage(e.health);
      Enemy e = g.enemies()[0];
      e.reset();
      e.x = 180;
      e.y = 870;
      e.detectionRadius = 4000;
      g.player.x = target - 18;
      g.player.y = deck.top() - 64;
      g.player.grounded = true;
      step(g, 10000, 0, 0);
      near(e.y + 70, deck.top(), "navigation reaches authored floor " + node + " x=" + e.x);
    }
  }

  static void tuningAndCombat() {
    Player p = new Player();
    near((float) p.effectiveDeflectWindow(), .32f, "new 320ms default deflect window");
    for (int i = 0; i < 8; i++) {
      p.setGuard(true, true);
      p.updateDefense(.02);
      p.setGuard(false, false);
    }
    near((float) p.effectiveDeflectWindow(), .05f, "failed taps still shrink to anti-spam floor");
    p.updateDefense(.8);
    near((float) p.effectiveDeflectWindow(), .32f, "penalty quiet recovery uses new window");
    p.setGuard(true, true);
    p.updateDefense(.321);
    Enemy e = new Enemy();
    p.receiveAttack(e);
    check(
        p.impact() == Player.Impact.BLOCK && p.health == 100,
        "held guard beyond new window remains a reliable block");
    p = new Player();
    e = new Enemy(new CombatConfig(.32, 10000, 0, 25, 35, 1, 1));
    for (int i = 0; i < 4; i++) {
      p.setGuard(true, true);
      p.updateDefense(.28);
      p.receiveAttack(e);
      check(p.impact() == Player.Impact.DEFLECT, "rapid intentional deflection " + i);
      p.setGuard(false, false);
    }
    near((float) p.effectiveDeflectWindow(), .32f, "rapid successes preserve full new window");
    p = new Player();
    e = new Enemy();
    p.x = e.x - Player.WIDTH - 20;
    p.powerUpgrade();
    p.update(1f / 120, 0, false, true);
    p.tryHit(e);
    check(
        e.health == 50 && e.posture.value() == 50,
        "upgraded actual hit doubles health and posture exactly once");
    p.tryHit(e);
    check(e.health == 50 && e.posture.value() == 50, "upgraded event cannot duplicate damage");
    p = new Player();
    p.powerUpgrade();
    e = new Enemy();
    p.setGuard(true, true);
    p.receiveAttack(e);
    check(e.posture.value() == 35, "power does not multiply defensive deflection transfer");
    p = new Player(CombatConfig.DEFAULT, 10000);
    e = new Enemy();
    p.x = e.x - Player.WIDTH - 20;
    int count = 0;
    while ((e.move() != EnemyMove.HEAVY_OVERHEAD || e.state() != Enemy.State.ATTACK_ACTIVE)
        && count++ < 4000) e.update(DT, p);
    check(count < 4000, "heavy remains in fixed move sequence");
    p.powerUpgrade();
    p.receiveAttack(e);
    check(
        p.health == 9960 && p.posture.value() == 50,
        "player power does not alter enemy heavy multipliers");
    Game g = new Game(CombatConfig.DEFAULT, 400, 400);
    for (Enemy foe : g.enemies()) foe.detectionRadius = 0;
    g.player.x = WorldDefinition.HP_X - 18;
    g.player.y = WorldDefinition.HP_FEET - 64;
    g.player.health = 1;
    tick(g, 0, 0, false, false);
    check(
        g.player.maxHealth == 450 && g.player.health == 450,
        "HP collectible uses configured base and fully heals");
    g.requestReset();
    g.confirmReset();
    check(
        g.player.maxHealth == 400 && g.player.health == 400,
        "reset restores configured base maximum");
    for (int fps : new int[] {60, 30, 20, 10, 5}) {
      g = geometry();
      for (int i = 0; i < fps * 6; i++) g.update(1f / fps, 1, false, false);
      near(g.player.x, 1260, "deliberate 180px/s continuous traversal at " + fps + " FPS");
      near(g.player.y + 64, 940, "gravity grounded across rates " + fps);
      check(
          g.simulationSteps() == 720 && g.droppedSeconds() == 0,
          "120Hz wall time preserved " + fps);
    }
  }

  public static void main(String[] args) {
    contentMode = args.length > 0 && args[0].equals("--content");
    solids();
    camera();
    traversal();
    enemies();
    allElevations();
    tuningAndCombat();
    System.out.println("Passed " + checks + " continuous castle/solid/camera/upgrade checks");
  }
}
