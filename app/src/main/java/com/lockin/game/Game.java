package com.lockin.game;

/** Simulation owned exclusively by the rendering thread. No Android dependencies. */
public final class Game {
  public static final double STEP = 1.0 / 120;
  public static final int MAX_STEPS_PER_UPDATE = 32;
  public static final double MAX_BACKLOG_SECONDS = .5;
  private long simulationSteps;
  private double lastAdvanceSeconds, droppedSeconds;

  public long simulationSteps() {
    return simulationSteps;
  }

  public double simulationSeconds() {
    return simulationSteps * STEP;
  }

  public double lastAdvanceSeconds() {
    return lastAdvanceSeconds;
  }

  public double backlogSeconds() {
    return accumulator;
  }

  public double droppedSeconds() {
    return droppedSeconds;
  }

  private double accumulator;
  private long restartVersion;

  public long restartVersion() {
    return restartVersion;
  }

  private boolean jumpQueued, attackQueued, dashQueued;
  public final Player player;

  /** Compatibility alias for the first enemy in the current area. */
  public Enemy enemy;

  public final AreaState[] areas;
  private final AreaState world;
  private final boolean continuous;
  private final LadderTraffic traffic = new LadderTraffic();
  private boolean hpCollected, powerCollected;
  private double noticeTime;
  private String notice = "";

  public boolean continuous() {
    return continuous;
  }

  public boolean hpCollected() {
    return hpCollected;
  }

  public boolean powerCollected() {
    return powerCollected;
  }

  public String notification() {
    return noticeTime > 0 ? notice : "";
  }

  public String regionName() {
    return continuous ? WorldDefinition.REGIONS[areaIndex()] : area().definition.name;
  }

  private void upgrades(float dt) {
    if (!continuous) return;
    noticeTime = Math.max(0, noticeTime - dt);
    if (!hpCollected && pickup(WorldDefinition.HP_X, WorldDefinition.HP_FEET)) {
      hpCollected = true;
      player.healthUpgrade();
      notice = "+50 MAX HP - FULL HEAL";
      noticeTime = 3;
    }
    if (!powerCollected && pickup(WorldDefinition.POWER_X, WorldDefinition.POWER_FEET)) {
      powerCollected = true;
      player.powerUpgrade();
      notice = "ATTACK POWER x2";
      noticeTime = 3;
    }
  }

  private boolean pickup(float x, float feet) {
    return CollisionManager.overlaps(
        player.x,
        player.y,
        player.x + Player.WIDTH,
        player.y + Player.HEIGHT,
        x - 16,
        feet - 40,
        x + 16,
        feet - 8);
  }

  public static Game screenWorld() {
    return screenWorld(CombatConfig.DEFAULT, Player.DEFAULT_MAX_HEALTH, Enemy.DEFAULT_MAX_HEALTH);
  }

  public static Game screenWorld(CombatConfig c, int p, int e) {
    return new Game(c, p, e, false);
  }

  private int areaIndex;
  private boolean resetPending, complete;
  private float transitionLock;
  private boolean combatArena;

  /**
   * Flat fixture for historical combat regressions; the Android game always uses authored areas.
   */
  public static Game combatArena() {
    return combatArena(CombatConfig.DEFAULT);
  }

  public static Game combatArena(CombatConfig c) {
    return combatArena(c, Player.DEFAULT_MAX_HEALTH, Enemy.DEFAULT_MAX_HEALTH);
  }

  public static Game combatArena(CombatConfig c, int p, int e) {
    Game g = screenWorld(c, p, e);
    g.combatArena = true;
    g.player.terrain = null;
    return g;
  }

  public int areaIndex() {
    return continuous ? Math.max(0, Math.min(2, (int) (player.x / 960))) : areaIndex;
  }

  public AreaState area() {
    return continuous ? world : areas[areaIndex];
  }

  public Enemy[] enemies() {
    return area().enemies;
  }

  public boolean resetPending() {
    return resetPending;
  }

  public boolean complete() {
    return complete;
  }

  public void requestReset() {
    resetPending = true;
    clearQueued();
    player.clearGuard();
  }

  public void cancelReset() {
    resetPending = false;
    clearQueued();
  }

  public void confirmReset() {
    if (resetPending) restart();
  }

  private void clearQueued() {
    accumulator = 0;
    jumpQueued = attackQueued = dashQueued = false;
  }

  private void enterArea(int index, boolean fromLeft) {
    areaIndex = index;
    enemy = enemies()[0];
    player.terrain = area().definition;
    player.finishTravel();
    player.x = fromLeft ? 48 : GameWorld.WIDTH - Player.WIDTH - 48;
    player.y = GameWorld.GROUND_Y - Player.HEIGHT;
    player.velocityY = 0;
    player.grounded = true;
    player.climbing = false;
    transitionLock = .4f;
    restartVersion++;
    jumpQueued = attackQueued = dashQueued = false;
  }

  private void exits(int move) {
    if (continuous) {
      if (move > 0
          && player.x >= WorldDefinition.WIDTH - Player.WIDTH - 1
          && player.grounded
          && player.y + Player.HEIGHT >= WorldDefinition.GROUND - .1f) {
        complete = true;
        clearQueued();
      }
      return;
    }
    if (combatArena || transitionLock > 0 || player.defeated()) return;
    if (move > 0
        && player.x >= GameWorld.WIDTH - Player.WIDTH - .01f
        && player.grounded
        && player.y + Player.HEIGHT >= GameWorld.GROUND_Y - .1f) {
      if (areaIndex < 2) enterArea(areaIndex + 1, true);
      else {
        complete = true;
        clearQueued();
      }
    } else if (move < 0
        && player.x <= .01f
        && areaIndex > 0
        && player.grounded
        && player.y + Player.HEIGHT >= GameWorld.GROUND_Y - .1f) enterArea(areaIndex - 1, false);
  }

  public Game() {
    this(CombatConfig.DEFAULT);
  }

  public Game(CombatConfig config) {
    this(config, Player.DEFAULT_MAX_HEALTH, Enemy.DEFAULT_MAX_HEALTH);
  }

  public Game(CombatConfig config, int playerMaxHealth, int enemyMaxHealth) {
    this(config, playerMaxHealth, enemyMaxHealth, true);
  }

  private Game(CombatConfig config, int playerMaxHealth, int enemyMaxHealth, boolean continuous) {
    this.continuous = continuous;
    player = new Player(config, playerMaxHealth);
    if (continuous) {
      world = new AreaState(WorldDefinition.WORLD, config, enemyMaxHealth);
      areas = new AreaState[] {world};
    } else {
      world = null;
      areas = new AreaState[3];
      for (int i = 0; i < areas.length; i++)
        areas[i] = new AreaState(AreaDefinition.AREAS[i], config, enemyMaxHealth);
    }
    enemy = enemies()[0];
    player.terrain = area().definition;
    if (continuous) {
      player.x = 180;
      player.y = WorldDefinition.GROUND - Player.HEIGHT;
    }
  }

  public void restart() {
    restartVersion++;
    simulationSteps = 0;
    lastAdvanceSeconds = droppedSeconds = 0;
    player.reset();
    for (AreaState area : areas) area.reset();
    hpCollected = powerCollected = false;
    noticeTime = 0;
    notice = "";
    if (continuous) {
      player.x = 180;
      player.y = WorldDefinition.GROUND - Player.HEIGHT;
    }
    areaIndex = 0;
    enemy = enemies()[0];
    player.terrain = combatArena ? null : area().definition;
    resetPending = complete = false;
    transitionLock = 0;
    clearQueued();
  }

  public void update(float dt, Controls.Snapshot input) {
    player.verticalInput = input.vertical;
    update(dt, input.move, input.jump, input.attack, input.guard, input.guardPresses, input.dash);
  }

  public void update(float dt, int move, boolean jump, boolean attack) {
    update(dt, move, jump, attack, false, false);
  }

  public void update(
      float dt, int move, boolean jump, boolean attack, boolean guard, boolean guardPressed) {
    update(dt, move, jump, attack, guard, guardPressed ? 1 : 0);
  }

  public void update(
      float dt, int move, boolean jump, boolean attack, boolean guard, int guardPresses) {
    update(dt, move, jump, attack, guard, guardPresses, false);
  }

  public void update(
      float dt,
      int move,
      boolean jump,
      boolean attack,
      boolean guard,
      int guardPresses,
      boolean dash) {
    lastAdvanceSeconds = 0;
    if (resetPending || complete) return;
    if ((Float.isNaN(dt) || Float.isInfinite(dt)) || dt <= 0) return;
    if (player.defeated()) {
      if (jump && combatArena) restart();
      return;
    }
    player.setGuard(guard, guardPresses);
    // Fixed simulation ticks keep contact/timing identical across render frame rates.
    // Preserve press edges when a render frame is shorter than a simulation tick.
    jumpQueued |= jump;
    attackQueued |= attack;
    dashQueued |= dash;
    // Preserve elapsed time across expensive render frames. Only pathological stalls
    // overflow the explicit backlog bound, and their discarded time is observable.
    accumulator += dt;
    if (accumulator > MAX_BACKLOG_SECONDS) {
      droppedSeconds += accumulator - MAX_BACKLOG_SECONDS;
      accumulator = MAX_BACKLOG_SECONDS;
    }
    int budget = MAX_STEPS_PER_UPDATE;
    while (accumulator + 1e-9 >= STEP && budget-- > 0) {
      simulationSteps++;
      lastAdvanceSeconds += STEP;
      float step = (float) STEP;
      accumulator = Math.max(0, accumulator - STEP);
      for (Enemy e : enemies()) CollisionManager.resolveBodies(player, e);
      transitionLock = Math.max(0, transitionLock - step);
      float oldPlayerX = player.x, oldPlayerY = player.y;
      player.updateDefense(step);
      for (Enemy e : enemies()) e.posture.update(step);
      // Eligible deathblow wins a simultaneous ATTACK/DASH press and cannot be cancelled.
      if (attackQueued) for (Enemy e : enemies()) if (player.tryDeathblow(e)) break;
      if (dashQueued) player.startDash(move);
      player.update(step, Math.max(-1, Math.min(1, move)), jumpQueued, attackQueued);
      player.setGuard(guard, 0);
      for (Enemy e : enemies())
        CollisionManager.resolvePlayerMovement(player, e, oldPlayerX, oldPlayerY);
      if (attackQueued) for (Enemy e : enemies()) if (player.tryDeathblow(e)) break;
      jumpQueued = attackQueued = dashQueued = false;
      upgrades(step);
      if (continuous) traffic.update(area(), step);
      // Player strikes resolve first: a lethal strike cancels the enemy attack.
      for (Enemy e : enemies()) player.tryHit(e);
      for (int i = 0; i < enemies().length; i++) {
        Enemy e = enemies()[i];
        float oldX = e.x, oldY = e.y;
        boolean navigating =
            !combatArena && area().navigators[i].update(e, player, area().definition, step);
        e.update(step, player, navigating);
        if (continuous) SolidPhysics.enemy(e, area().definition, oldX, oldY, step);
        else if (!combatArena)
          TerrainPhysics.enemy(e, area().definition, e.climbing ? oldY : e.y, step);
        CollisionManager.resolveEnemyMovement(e, player, oldX, oldY);
        for (int j = 0; j < enemies().length; j++)
          if (i != j) {
            if (continuous) CollisionManager.resolveEnemies(e, enemies()[j], oldX, oldY);
            else CollisionManager.resolveEnemies(e, enemies()[j], oldX);
          }
        e.tryHit(player);
      }
      exits(move);
      if (player.defeated()) {
        accumulator = 0;
        break;
      }
    }
  }
}
