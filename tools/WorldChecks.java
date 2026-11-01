package com.lockin.game;

/** Integration checks use production areas, fixed ticks, real navigation and combat. */
public final class WorldChecks {
  static int checks;
  static final float DT = 1f / 120;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static void near(float a, float b, String message) {
    check(Math.abs(a - b) < .15f, message + " (" + a + ", " + b + ")");
  }

  static void tick(Game g, int move, int vertical, boolean jump, boolean attack, boolean dash) {
    Controls.Snapshot in = new Controls.Snapshot();
    in.move = move;
    in.vertical = vertical;
    in.jump = jump;
    in.attack = attack;
    in.dash = dash;
    g.update(DT, in);
  }

  static void step(Game g, int count, int move, int vertical) {
    for (int i = 0; i < count; i++) tick(g, move, vertical, false, false, false);
  }

  static void safe(Game g) {
    for (AreaState a : g.areas) for (Enemy e : a.enemies) e.detectionRadius = 0;
  }

  static void exit(Game g, int direction) {
    g.player.finishTravel();
    g.player.x = direction > 0 ? 924 : 0;
    g.player.y = 316;
    g.player.velocityY = 0;
    g.player.grounded = true;
    step(g, 60, direction, 0);
  }

  static void floor(Game g, float x) {
    g.player.x = x;
    g.player.y = 316;
    g.player.velocityY = 0;
    g.player.grounded = true;
  }

  static void platforms() {
    Game g = new Game();
    safe(g);
    floor(g, 340);
    tick(g, 0, 0, true, false, false);
    step(g, 100, 0, 0);
    near(g.player.y + 64, 268, "jump lands on training deck");
    check(g.player.grounded, "deck grounded");
    tick(g, 0, 1, false, false, false);
    step(g, 70, 0, 0);
    near(g.player.y + 64, 380, "down drops through one-way to solid floor");
    floor(g, 340);
    g.player.y = 0;
    g.player.velocityY = 3000;
    g.player.grounded = false;
    tick(g, 0, 0, false, false, false);
    step(g, 30, 0, 0);
    near(g.player.y + 64, 268, "fast descent cannot tunnel through deck");
    floor(g, 600);
    tick(g, 0, 0, true, false, false);
    step(g, 120, 0, 0);
    near(g.player.y + 64, 380, "jump beside deck returns to floor");
    floor(g, 448 - 18);
    step(g, 40, 0, -1);
    check(g.player.climbing, "up enters ladder");
    float y = g.player.y;
    step(g, 30, 0, 0);
    near(g.player.y, y, "ladder hold suppresses gravity");
    step(g, 100, 0, -1);
    near(g.player.y + 64, 268, "ladder exits onto deck");
    check(!g.player.climbing && g.player.grounded, "top is grounded");
    step(g, 120, 0, 1);
    near(g.player.y + 64, 380, "down ladder reaches floor");
    step(g, 30, 0, -1);
    tick(g, 1, 0, false, false, false);
    check(!g.player.climbing, "horizontal input exits ladder");
    step(g, 120, 1, 0);
    near(g.player.y + 64, 380, "gravity resumes after ladder exit");
    floor(g, 350);
    g.player.y = 204;
    tick(g, 1, 0, false, false, true);
    step(g, 23, 1, 0);
    near(g.player.y + 64, 268, "dash remains on platform while supported");
    step(g, 60, 0, 0);
    floor(g, 600);
    g.enemy.x = 680;
    g.enemy.y = 310;
    tick(g, 1, 0, false, false, true);
    step(g, 23, 0, 0);
    check(g.player.x + 36 <= g.enemy.x + .01f, "dash cannot pass living enemy");
    step(g, 60, 0, 0);
    floor(g, 600);
    g.player.y = 170;
    g.player.grounded = false;
    tick(g, 1, 0, false, false, true);
    step(g, 20, 0, 0);
    check(g.player.x > g.enemy.x + 44, "air dash crosses above non-overlapping enemy");
    floor(g, 910);
    tick(g, 1, 0, false, false, true);
    check(g.player.x <= 924, "dash respects boundary");
  }

  static void persistenceReset() {
    Game g = new Game();
    safe(g);
    check(g.areas.length == 3, "exactly three areas");
    check(
        g.areas[0].enemies.length == 1
            && g.areas[1].enemies.length == 1
            && g.areas[2].enemies.length == 3,
        "1/1/3 enemies");
    g.enemy.takeDamage(g.enemy.health);
    g.player.health = 73;
    g.player.posture.add(12);
    double posture = g.player.posture.value();
    exit(g, 1);
    check(g.areaIndex() == 1, "right enters courtyard");
    check(g.player.health == 73, "transition preserves health");
    check(g.player.posture.value() <= posture, "transition does not add posture");
    exit(g, -1);
    check(g.areaIndex() == 0 && g.enemy.defeated(), "return preserves enemy death");
    exit(g, 1);
    g.enemy.takeDamage(g.enemy.health);
    exit(g, 1);
    check(g.areaIndex() == 2, "right enters castle");
    for (Enemy e : g.enemies()) check(!e.defeated(), "three castle enemies start alive");
    exit(g, -1);
    check(
        g.areaIndex() == 1 && g.enemy.defeated(),
        "castle returns to courtyard with persistent death");
    for (int area = 0; area < 3; area++) {
      g.restart();
      safe(g);
      for (int i = 0; i < area; i++) exit(g, 1);
      g.player.health = 42;
      g.enemy.health = 1;
      float x = g.player.x;
      long version = g.restartVersion();
      g.requestReset();
      step(g, 100, 1, 0);
      near(g.player.x, x, "confirmation pauses movement");
      check(
          g.player.health == 42 && g.enemy.health == 1,
          "confirmation pauses enemy and preserves state");
      g.cancelReset();
      check(
          !g.resetPending() && g.areaIndex() == area && g.player.health == 42,
          "cancel retains run");
      g.requestReset();
      g.confirmReset();
      check(
          g.areaIndex() == 0 && !g.resetPending() && !g.complete(), "confirm returns to area one");
      near(g.player.x, 180, "reset initial spawn");
      check(g.player.health == g.player.maxHealth && g.player.posture.value() == 0, "reset vitals");
      check(
          !g.player.dashing()
              && !g.player.attacking()
              && !g.player.climbing
              && g.player.dashCooldownRemaining() == 0,
          "reset temporary movement/attacks");
      for (AreaState a : g.areas)
        for (Enemy e : a.enemies)
          check(
              e.health == e.maxHealth
                  && e.posture.value() == 0
                  && e.nextMove() == EnemyMove.NORMAL_SLASH,
              "all enemy states restored");
      check(g.restartVersion() > version, "reset invalidates visual states");
    }
    g.player.startDash(1);
    g.requestReset();
    g.confirmReset();
    check(
        !g.player.dashing() && g.player.dashCooldownRemaining() == 0,
        "reset clears active dash and cooldown");
    safe(g);
    exit(g, 1);
    exit(g, 1);
    exit(g, 1);
    check(g.complete(), "final destination completes prototype");
    float x = g.player.x;
    step(g, 60, -1, 0);
    near(g.player.x, x, "completion freezes gameplay");
    g.requestReset();
    g.cancelReset();
    check(g.complete(), "cancel keeps completion");
    g.requestReset();
    g.confirmReset();
    check(!g.complete() && g.areaIndex() == 0, "reset after completion");
    g.player.takeDamage(g.player.health);
    g.requestReset();
    g.confirmReset();
    check(!g.player.defeated(), "reset after defeat");
  }

  static void verticalBodies() {
    Player p = new Player();
    p.x = 300;
    p.y = 200;
    Enemy e = new Enemy();
    e.x = 300;
    e.y = 130;
    float x = p.x;
    float y = p.y;
    e.y = 140;
    CollisionManager.resolveEnemyMovement(e, p, e.x, 130);
    near(e.y + 70, p.y, "descending enemy stops above player");
    near(p.x, x, "vertical enemy cannot push player horizontally");
    near(p.y, y, "vertical enemy cannot push player vertically");
    e.y = 264;
    float old = e.y;
    e.y = 260;
    CollisionManager.resolveEnemyMovement(e, p, e.x, old);
    near(e.y, p.y + 64, "climbing enemy stops below player");
  }

  static void allActive() {
    Game g = new Game(CombatConfig.DEFAULT, 10000, 100);
    safe(g);
    exit(g, 1);
    exit(g, 1);
    floor(g, 500);
    for (Enemy e : g.enemies()) e.detectionRadius = 340;
    step(g, 10, 0, 0);
    for (Enemy e : g.enemies())
      check(e.state() != Enemy.State.IDLE, "all three castle enemies act simultaneously");
  }

  static void independent() {
    Game g = new Game();
    safe(g);
    exit(g, 1);
    exit(g, 1);
    Enemy a = g.enemies()[0], b = g.enemies()[1], c = g.enemies()[2];
    a.receiveHit(25, 20);
    check(b.health == b.maxHealth && b.posture.value() == 0, "health/posture independent");
    a.takeDamage(a.health);
    check(!b.defeated() && !c.defeated(), "death independent");
    g.restart();
    safe(g);
    exit(g, 1);
    exit(g, 1);
    a = g.enemies()[0];
    b = g.enemies()[1];
    c = g.enemies()[2];
    floor(g, 450);
    a.x = 390;
    a.y = 310;
    b.x = 506;
    b.y = 310;
    c.x = 750;
    c.y = 310;
    a.detectionRadius = b.detectionRadius = 340;
    step(g, 63, 0, 0);
    check(g.player.health == 60, "simultaneous independent slashes both damage once");
    check(g.player.posture.value() == 50, "each independent hit transfers posture");
    step(g, 10, 0, 0);
    check(g.player.health == 60, "active events cannot duplicate damage");
    CharacterAnimations aa = new CharacterAnimations(), bb = new CharacterAnimations();
    aa.update(g, 0, a);
    bb.update(g, 0, b);
    a.takeDamage(a.health);
    aa.update(g, .02, a);
    bb.update(g, .02, b);
    check(
        aa.enemyPose() == CharacterAnimations.Pose.DEATH
            && bb.enemyPose() != CharacterAnimations.Pose.DEATH,
        "independent animation death state");
  }

  static void determinism() {
    Game a = new Game(), b = new Game();
    safe(a);
    safe(b);
    floor(a, 430);
    floor(b, 430);
    Controls.Snapshot in = new Controls.Snapshot();
    in.vertical = -1;
    for (int i = 0; i < 120; i++) a.update(1f / 120, in);
    for (int i = 0; i < 30; i++) b.update(1f / 30, in);
    near(a.player.y, b.player.y, "ladder simulation independent of render frame rate");
    near(a.player.x, b.player.x, "ladder alignment independent of render frame rate");
    a.restart();
    b.restart();
    safe(a);
    safe(b);
    a.enemy.takeDamage(100);
    b.enemy.takeDamage(100);
    in.vertical = 0;
    in.move = 1;
    for (int i = 0; i < 600; i++) a.update(1f / 120, in);
    for (int i = 0; i < 150; i++) b.update(1f / 30, in);
    check(a.areaIndex() == b.areaIndex(), "area transition frame-rate invariant");
    near(a.player.x, b.player.x, "transition position frame-rate invariant");
  }

  static void dropAndUnreachable() {
    var floor = new AreaDefinition.Platform(0, 960, 380, false);
    var deck = new AreaDefinition.Platform(320, 576, 268, true);
    var area =
        new AreaDefinition(
            "drop fixture",
            new AreaDefinition.Platform[] {floor, deck},
            new AreaDefinition.Ladder[0],
            new AreaDefinition.Spawn[0],
            new AreaDefinition.Link[] {
              new AreaDefinition.Link(1, 0, AreaDefinition.Travel.DROP, 290, 280)
            });
    Enemy e = new Enemy();
    e.x = 400;
    e.y = 198;
    e.detectionRadius = 1000;
    Player p = new Player();
    p.x = 100;
    EnemyNavigator nav = new EnemyNavigator();
    for (int i = 0; i < 400; i++) {
      nav.update(e, p, area, DT);
      TerrainPhysics.enemy(e, area, e.y, DT);
    }
    near(e.y + 70, 380, "navigation walks off deck and falls to floor");
    var isolated =
        new AreaDefinition(
            "isolated fixture",
            new AreaDefinition.Platform[] {floor, deck},
            new AreaDefinition.Ladder[0],
            new AreaDefinition.Spawn[0],
            new AreaDefinition.Link[0]);
    e.reset();
    e.x = 400;
    e.y = 198;
    e.detectionRadius = 1000;
    nav.reset();
    float x = e.x;
    for (int i = 0; i < 100; i++) {
      boolean path = nav.update(e, p, isolated, DT);
      e.update(DT, p, path);
      TerrainPhysics.enemy(e, isolated, e.y, DT);
    }
    near(e.x, x, "unreachable target waits without horizontal jitter");
    near(e.y + 70, 268, "unreachable target does not teleport");
  }

  static void navigation() {
    for (int index = 0; index < 3; index++) {
      Game g = new Game(CombatConfig.DEFAULT, 10000, 100);
      safe(g);
      for (int i = 0; i < index; i++) exit(g, 1);
      for (Enemy e : g.enemies()) e.takeDamage(e.health);
      Enemy e = g.enemies()[0];
      e.reset();
      e.x = 300;
      e.y = 310;
      e.detectionRadius = 1000;
      var a = g.area().definition;
      var deck = a.platform(1);
      g.player.x = (deck.left() + deck.right()) / 2 - 18;
      g.player.y = deck.top() - 64;
      g.player.velocityY = 0;
      g.player.grounded = true;
      step(g, 500, 0, 0);
      check(
          e.y >= g.player.y + Player.HEIGHT - .01f,
          "occupied ladder exit waits without pushing player " + index);
      g.player.x += 80;
      step(g, 600, 0, 0);
      near(e.y + 70, deck.top(), "enemy reaches elevation in area " + index);
      check(e.state() != Enemy.State.IDLE, "navigation resumes engagement");
      floor(g, 120);
      step(g, 1000, 0, 0);
      near(e.y + 70, 380, "enemy descends reachable ladder " + index);
    }
    // Courtyard has a direct, physically simulated jump between its two decks.
    Game g = new Game();
    safe(g);
    exit(g, 1);
    Enemy e = g.enemy;
    e.reset();
    e.x = 368;
    e.y = 214;
    e.detectionRadius = 1000;
    g.player.x = 610;
    g.player.y = 124;
    g.player.grounded = true;
    boolean airborne = false;
    for (int i = 0; i < 800; i++) {
      tick(g, 0, 0, false, false, false);
      airborne |= e.velocityY < 0;
    }
    check(airborne, "navigation performs real jump");
    near(e.y + 70, 188, "jump reaches upper courtyard deck");
  }

  static void walkTo(Game g, float x) {
    int steps = 0;
    while (Math.abs(g.player.x - x) > 2.2f && steps++ < 1500)
      tick(g, g.player.x < x ? 1 : -1, 0, false, false, false);
    check(steps < 1500, "scripted route reaches horizontal position " + x);
  }

  static void killByAttack(Game g, Enemy target) {
    int count = 0;
    while (!target.defeated() && count++ < 1500) {
      float dx = target.x + 22 - g.player.x - 18;
      g.player.facingRight = dx > 0;
      int move = Math.abs(dx) > 80 ? (dx > 0 ? 1 : -1) : 0;
      tick(g, move, 0, false, true, false);
    }
    check(target.defeated(), "ordinary player attacks defeat independent target");
  }

  static void playableRun() {
    Game g = new Game();
    safe(g);
    killByAttack(g, g.enemy);
    for (int i = 0; i < 1000 && g.areaIndex() == 0; i++) tick(g, 1, 0, false, false, false);
    check(g.areaIndex() == 1, "walk through first exit without teleporting");
    walkTo(g, 544 - 18);
    step(g, 250, 0, -1);
    near(g.player.y + 64, 188, "player reaches courtyard upper route");
    killByAttack(g, g.enemy);
    walkTo(g, 544 - 18);
    step(g, 250, 0, 1);
    near(g.player.y + 64, 380, "player returns to courtyard floor");
    for (int i = 0; i < 1000 && g.areaIndex() == 1; i++) tick(g, 1, 0, false, false, false);
    check(g.areaIndex() == 2, "walk through second exit");
    killByAttack(g, g.enemies()[0]);
    walkTo(g, 352 - 18);
    step(g, 200, 0, -1);
    near(g.player.y + 64, 268, "player reaches castle lower deck");
    killByAttack(g, g.enemies()[1]);
    walkTo(g, 352 - 18);
    step(g, 200, 0, 1);
    walkTo(g, 672 - 18);
    step(g, 250, 0, -1);
    near(g.player.y + 64, 172, "player reaches castle upper deck");
    killByAttack(g, g.enemies()[2]);
    walkTo(g, 672 - 18);
    step(g, 250, 0, 1);
    step(g, 200, 1, 0);
    check(g.complete(), "entire three-area run reaches completion through gameplay inputs");
    for (AreaState area : g.areas)
      for (Enemy e : area.enemies) check(e.defeated(), "all five defeated during scripted run");
    g.requestReset();
    g.confirmReset();
    check(g.areaIndex() == 0 && !g.complete(), "full run can replay after confirmed reset");
  }

  public static void main(String[] args) {
    playableRun();
    platforms();
    persistenceReset();
    independent();
    verticalBodies();
    allActive();
    navigation();
    dropAndUnreachable();
    determinism();
    System.out.println("Passed " + checks + " world/platform/navigation/reset checks");
  }
}
