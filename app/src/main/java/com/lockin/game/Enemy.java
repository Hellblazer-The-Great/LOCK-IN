package com.lockin.game;

import com.lockin.game.content.*;

/** Deterministic melee opponent. Facing is committed from windup through recovery. */
public final class Enemy {
  public final EntityDef definition;
  private final Content content;
  private final BehaviorDef behavior;
  public final AttackRun attackRun = new AttackRun();
  public final float width, height, speed, gravity, jumpSpeed, climbSpeed, airSpeed, acceleration;
  private float configuredCooldown, configuredRecovery, velocityX;
  private long randomState;
  private boolean configuredMove;

  public String attackId() {
    return attackRun.definition() == null
        ? (definition == null ? move.name() : definition.attack)
        : attackRun.definition().id;
  }

  public enum State {
    IDLE,
    CHASE,
    ATTACK_WINDUP,
    ATTACK_ACTIVE,
    ATTACK_RECOVERY,
    POSTURE_BROKEN,
    DEAD
  }

  public static final float WIDTH = 44, HEIGHT = 70, SPEED = 110;
  public static final float DEFAULT_DETECTION_RADIUS = 340, MELEE_DISTANCE = 30;
  public static final float ATTACK_RANGE = 64, WINDUP = .5f, ACTIVE = .18f, RECOVERY = .7f;
  public static final int DAMAGE = 20;
  public static final double POSTURE_DAMAGE = 25;
  private static final EnemyMove[] SEQUENCE = EnemyMove.values();
  private EnemyMove move = EnemyMove.NORMAL_SLASH;
  private int nextMove, strike;
  private boolean moveInProgress;
  private float betweenMoves;
  public float x = 670, y = GameWorld.GROUND_Y - HEIGHT;

  /** Center-to-center distance in world pixels; zero disables detection for practice/tests. */
  public float velocityY;

  AreaDefinition terrain;

  public float worldWidth() {
    return terrain == null ? GameWorld.WIDTH : terrain.width;
  }

  public boolean grounded = true, climbing;
  public float detectionRadius = DEFAULT_DETECTION_RADIUS;
  public static final int DEFAULT_MAX_HEALTH = 100;
  public final int maxHealth;
  public int health;
  public final Posture posture;
  public boolean facingRight;
  private State state = State.IDLE;
  private float stateTime, phaseDuration;
  private boolean hitThisAttack;

  public Enemy() {
    this(CombatConfig.DEFAULT);
  }

  public Enemy(CombatConfig config) {
    this(config, DEFAULT_MAX_HEALTH);
  }

  public Enemy(CombatConfig config, int maxHealth) {
    this(config, maxHealth, null, null);
  }

  public Enemy(EntityDef definition, Content content) {
    this(definition.combat, definition.maxHealth, definition, content);
  }

  private Enemy(CombatConfig config, int maxHealth, EntityDef definition, Content content) {
    this.definition = definition;
    this.content = content;
    behavior = definition == null ? null : content.behaviors.get(definition.behavior);
    width = definition == null ? WIDTH : definition.width;
    height = definition == null ? HEIGHT : definition.height;
    speed = definition == null ? SPEED : definition.speed;
    gravity = definition == null ? GameWorld.GRAVITY : definition.gravity;
    jumpSpeed = definition == null ? 590 : definition.jumpSpeed;
    climbSpeed = definition == null ? 140 : definition.climbSpeed;
    airSpeed = definition == null ? 250 : definition.airSpeed;
    acceleration = definition == null ? 0 : definition.acceleration;
    this.maxHealth = Math.max(0, maxHealth);
    posture = new Posture(config);
    reset();
  }

  /** Actual phase duration also handles recovery after a posture break. */
  public float phaseProgress() {
    if (definition != null && attackRun.running()) return attackRun.progress();
    return Vitals.fraction(phaseDuration - stateTime, phaseDuration);
  }

  public EnemyMove move() {
    return move;
  }

  public EnemyMove nextMove() {
    if (definition != null) {
      String style = content.attacks.get(behavior.attacks.get(nextMove)).style;
      return style.equals("heavy")
          ? EnemyMove.HEAVY_OVERHEAD
          : style.equals("double") ? EnemyMove.DOUBLE_SLASH : EnemyMove.NORMAL_SLASH;
    }
    return SEQUENCE[nextMove];
  }

  public int strikeNumber() {
    if (definition != null) return attackRun.strike() + 1;
    return strike + 1;
  }

  public int damageMultiplier() {
    return move.damageMultiplier;
  }

  public int healthDamage() {
    return definition == null ? DAMAGE * damageMultiplier() : currentAttack().damage;
  }

  public double postureDamage() {
    return definition == null ? POSTURE_DAMAGE * damageMultiplier() : currentAttack().postureDamage;
  }

  private void resolveMove() {
    if (definition != null) {
      resolveConfigured();
      return;
    }
    if (moveInProgress) {
      nextMove = (nextMove + 1) % SEQUENCE.length;
      moveInProgress = false;
    }
  }

  private void breakPosture() {
    velocityX = 0;
    resolveMove();
    enter(State.POSTURE_BROKEN, 0);
    hitThisAttack = true;
  }

  public void receiveHit(int healthDamage, double postureDamage) {
    if (defeated()) return;
    // Both components belong to this living contact, including a lethal health hit.
    posture.add(postureDamage);
    takeDamage(healthDamage);
    if (!defeated() && posture.broken()) breakPosture();
    else if (definition != null && attackRun.running() && attackRun.definition().interruptOnHit) {
      resolveConfigured();
      configuredRecovery = (float) behavior.staggerRecovery;
      enter(State.ATTACK_RECOVERY, configuredRecovery);
    }
  }

  public float healthFraction() {
    return Vitals.fraction(health, maxHealth);
  }

  public void deflected(double amount) {
    if (defeated()) return;
    posture.add(amount);
    if (posture.broken()) breakPosture();
  }

  public State state() {
    return defeated() ? State.DEAD : posture.broken() ? State.POSTURE_BROKEN : state;
  }

  public void reset() {
    attackRun.reset();
    configuredMove = false;
    configuredCooldown = configuredRecovery = velocityX = 0;
    if (behavior != null) {
      randomState = behavior.seed;
      detectionRadius = behavior.detection;
    }
    velocityY = 0;
    grounded = true;
    climbing = false;
    posture.reset();
    move = EnemyMove.NORMAL_SLASH;
    nextMove = strike = 0;
    moveInProgress = false;
    betweenMoves = 0;
    x = 670;
    y = GameWorld.GROUND_Y - height;
    health = maxHealth;
    facingRight = false;
    state = State.IDLE;
    stateTime = phaseDuration = 0;
    hitThisAttack = false;
  }

  public void takeDamage(int amount) {
    if (amount > 0) posture.pressure();
    health = (int) Math.max(0, (long) health - Math.max(0, amount));
    if (defeated()) {
      attackRun.cancel();
      configuredMove = false;
      state = State.DEAD;
      stateTime = 0;
      nextMove = strike = 0;
      move = EnemyMove.NORMAL_SLASH;
      moveInProgress = false;
    }
  }

  public boolean defeated() {
    return health <= 0;
  }

  public boolean detects(Player player) {
    float dx = player.x + player.width / 2 - (x + width / 2);
    float dy = player.y + player.height / 2 - (y + height / 2);
    return detectionRadius > 0 && dx * dx + dy * dy <= detectionRadius * detectionRadius;
  }

  private void enter(State next, float duration) {
    state = next;
    stateTime = phaseDuration = duration;
  }

  public void update(float dt, Player player) {
    update(dt, player, false);
  }

  public void update(float dt, Player player, boolean navigating) {
    if (!Float.isFinite(dt) || dt <= 0 || player.defeated()) return;
    if (definition != null) {
      updateConfigured(dt, player, navigating);
      return;
    }
    if (defeated()) {
      state = State.DEAD;
      return;
    }
    if (posture.broken()) {
      breakPosture();
      return;
    }
    if (state == State.POSTURE_BROKEN) {
      enter(State.ATTACK_RECOVERY, RECOVERY);
      return;
    }
    if (state == State.ATTACK_WINDUP
        || state == State.ATTACK_ACTIVE
        || state == State.ATTACK_RECOVERY) {
      stateTime = Math.max(0, stateTime - dt);
      if (stateTime == 0) {
        switch (state) {
          case ATTACK_WINDUP:
            hitThisAttack = false;
            enter(State.ATTACK_ACTIVE, move.active);
            break;
          case ATTACK_ACTIVE:
            if (strike + 1 < move.strikes) {
              strike++;
              enter(State.ATTACK_WINDUP, move.betweenStrikes);
            } else enter(State.ATTACK_RECOVERY, move.recovery);
            break;
          default:
            resolveMove();
            betweenMoves = EnemyMove.BETWEEN_MOVES;
            enter(State.IDLE, 0);
            break;
        }
      }
      return;
    }
    betweenMoves = Math.max(0, betweenMoves - dt);
    if (!detects(player)) {
      state = State.IDLE;
      return;
    }
    float dx = player.x + player.width / 2 - (x + width / 2);
    if (dx != 0) facingRight = dx > 0;
    float gap = Math.abs(dx) - (width + player.width) / 2;
    boolean sameHeight = player.y < y + height - 8 && player.y + player.height > y + 10;
    if (!navigating && grounded && !climbing && gap <= MELEE_DISTANCE + .001f && sameHeight) {
      if (betweenMoves > 0) {
        state = State.IDLE;
        return;
      }
      move = SEQUENCE[nextMove];
      strike = 0;
      moveInProgress = true;
      hitThisAttack = false;
      enter(State.ATTACK_WINDUP, move.windup);
    } else {
      state = State.CHASE;
      float distance = Math.min(speed * dt, Math.max(0, gap - MELEE_DISTANCE));
      if (!navigating) x += (facingRight ? 1 : -1) * distance;
      x = Math.max(0, Math.min(worldWidth() - width, x));
    }
  }

  public float attackLeft() {
    if (definition != null && attackRun.definition() != null)
      return attackRun.left(x, width, facingRight);
    return facingRight ? x + width : x - move.reach;
  }

  public float attackRight() {
    return attackLeft()
        + (definition != null && attackRun.definition() != null
            ? attackRun.hit().width()
            : move.reach);
  }

  public float attackTop() {
    return y
        + (definition != null && attackRun.definition() != null
            ? attackRun.hit().top()
            : move.topOffset);
  }

  public float attackBottom() {
    return attackTop()
        + (definition != null && attackRun.definition() != null
            ? attackRun.hit().height()
            : move.hitboxHeight);
  }

  public void tryHit(Player player) {
    if (state() == State.ATTACK_ACTIVE
        && !defeated()
        && !player.defeated()
        && (definition == null ? !hitThisAttack : attackRun.canHit())
        && CollisionManager.overlaps(
            attackLeft(),
            attackTop(),
            attackRight(),
            attackBottom(),
            player.x,
            player.y,
            player.x + player.width,
            player.y + player.height)) {
      player.receiveAttack(this);
      hitThisAttack = true;
      if (definition != null) attackRun.consume();
    }
  }

  private AttackDef currentAttack() {
    return attackRun.definition() == null
        ? content.attacks.get(definition.attack)
        : attackRun.definition();
  }

  public boolean blockable() {
    return definition == null || currentAttack().blockable;
  }

  public boolean deflectable() {
    return definition == null || currentAttack().deflectable;
  }

  public double blockDamage() {
    return definition == null ? 25 * damageMultiplier() : currentAttack().blockDamage;
  }

  public double deflectDamage() {
    return definition == null ? 35 * damageMultiplier() : currentAttack().deflectDamage;
  }

  public boolean canChase() {
    return behavior == null || behavior.chase;
  }

  public String nextAttackId() {
    return definition == null ? nextMove().name() : behavior.attacks.get(nextMove);
  }

  public void seed(long salt) {
    if (behavior != null) randomState = behavior.seed ^ salt;
  }

  private void resolveConfigured() {
    if (configuredMove) {
      nextMove = (nextMove + 1) % behavior.attacks.size();
      configuredMove = false;
      betweenMoves = (float) behavior.betweenMoves;
    }
    attackRun.cancel();
  }

  private int select() {
    if (behavior.selection.equals("sequence")) return nextMove;
    randomState = randomState * 6364136223846793005L + 1442695040888963407L;
    double total = 0;
    for (double w : behavior.weights) total += w;
    double pick = ((randomState >>> 11) * 0x1.0p-53) * total;
    for (int i = 0; i < behavior.weights.size(); i++) {
      pick -= behavior.weights.get(i);
      if (pick < 0) return i;
    }
    return behavior.attacks.size() - 1;
  }

  private void updateConfigured(float dt, Player p, boolean navigating) {
    if (defeated()) {
      state = State.DEAD;
      return;
    }
    configuredCooldown = Math.max(0, configuredCooldown - dt);
    if (posture.broken()) {
      resolveConfigured();
      state = State.POSTURE_BROKEN;
      return;
    }
    if (state == State.POSTURE_BROKEN) {
      configuredRecovery = (float) behavior.staggerRecovery;
      enter(State.ATTACK_RECOVERY, configuredRecovery);
    }
    if (configuredRecovery > 0) {
      configuredRecovery = Math.max(0, configuredRecovery - dt);
      stateTime = configuredRecovery;
      if (configuredRecovery == 0) state = State.IDLE;
      return;
    }
    if (attackRun.running()) {
      attackRun.advance(dt);
      if (!attackRun.running()) {
        resolveConfigured();
        state = State.IDLE;
      } else syncPhase();
      return;
    }
    betweenMoves = Math.max(0, betweenMoves - dt);
    if (!detects(p)) {
      state = State.IDLE;
      return;
    }
    float dx = p.x + p.width / 2 - (x + width / 2), gap = Math.abs(dx) - (width + p.width) / 2;
    if (behavior.facePlayer && dx != 0) facingRight = dx > 0;
    boolean sameHeight = p.y < y + height - 8 && p.y + p.height > y + 10;
    if (!navigating && grounded && !climbing && gap <= behavior.engagement + .001f && sameHeight) {
      if (betweenMoves > 0 || configuredCooldown > 0) {
        state = State.IDLE;
        return;
      }
      AttackDef a = content.attacks.get(behavior.attacks.get(select()));
      move =
          a.style.equals("heavy")
              ? EnemyMove.HEAVY_OVERHEAD
              : a.style.equals("double") ? EnemyMove.DOUBLE_SLASH : EnemyMove.NORMAL_SLASH;
      attackRun.start(a);
      configuredMove = true;
      configuredCooldown = (float) a.cooldown;
      syncPhase();
    } else {
      state = behavior.chase ? State.CHASE : State.IDLE;
      if (!navigating && behavior.chase) {
        float target = Math.copySign(speed, dx);
        if (acceleration == 0) velocityX = target;
        else
          velocityX +=
              Math.copySign(
                  Math.min(Math.abs(target - velocityX), acceleration * dt), target - velocityX);
        float distance = Math.min(Math.abs(velocityX) * dt, Math.max(0, gap - behavior.engagement));
        x += Math.copySign(distance, dx);
      }
      x = Math.max(0, Math.min(worldWidth() - width, x));
    }
  }

  public float approachDelta(float dx, float movementSpeed, float dt) {
    float target = Math.copySign(movementSpeed, dx);
    if (acceleration == 0) velocityX = target;
    else
      velocityX +=
          Math.copySign(
              Math.min(Math.abs(target - velocityX), acceleration * dt), target - velocityX);
    return Math.copySign(Math.min(Math.abs(dx), Math.abs(velocityX) * dt), dx);
  }

  private void syncPhase() {
    switch (attackRun.phase()) {
      case WINDUP:
        state = State.ATTACK_WINDUP;
        break;
      case ACTIVE:
        state = State.ATTACK_ACTIVE;
        break;
      case RECOVERY:
        state = State.ATTACK_RECOVERY;
        break;
      default:
        state = State.IDLE;
    }
  }
}
