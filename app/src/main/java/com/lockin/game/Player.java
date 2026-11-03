package com.lockin.game;

import com.lockin.game.content.*;

public final class Player {
  public final EntityDef definition;
  public final Inventory inventory;
  public final CombatState combat=new CombatState();
  private int healthUpgradeBonus;
  private final Content content;
  public final AttackRun attackRun = new AttackRun();
  public final float width, height, speed, gravity, jumpSpeed, climbSpeed, airSpeed, acceleration;
  private float velocityX;
  private double configuredCooldown;
  private ComboDef.Followup bufferedCombo;
  private final double dashDuration,
      dashSpeed,
      dashCooldownDuration,
      iframeStart,
      iframeEnd,
      deathblowDuration;
  public static final float WIDTH = 36, HEIGHT = 64, SPEED = 180;
  public static final float ATTACK_RANGE = 68, ATTACK_DURATION = .16f, COOLDOWN = .45f;
  public static final int DAMAGE = 25;
  public static final double POSTURE_DAMAGE = 25;
  public static final double DASH_DURATION = .180, DASH_IFRAMES = .100, DASH_COOLDOWN = .450;
  public static final double DASH_IFRAME_START = (DASH_DURATION - DASH_IFRAMES) / 2;
  public static final double DASH_IFRAME_END = DASH_IFRAME_START + DASH_IFRAMES;
  public static final float DASH_SPEED = 950;
  public static final double DEATHBLOW_DURATION = .30;
  private double dashAge = DASH_DURATION, dashCooldown, deathblowTime;
  private int dashDirection;
  private boolean guardNeedsFreshPress;
  public float x = 180, y = GameWorld.GROUND_Y - HEIGHT;
  public float velocityY;
  public boolean climbing;
  int ladder;
  float dropTime;
  AreaDefinition terrain;
  int verticalInput;
  public static final int DEFAULT_MAX_HEALTH = 100;
  public final int baseMaxHealth;
  public int maxHealth;
  private int attackMultiplier = 1;

  public int attackMultiplier() {
    return attackMultiplier;
  }

  public void healthUpgrade() {
    healthUpgrade(50);
  }

  public void healthUpgrade(int amount) {
    healthUpgradeBonus=(int)Math.min(Integer.MAX_VALUE,(long)healthUpgradeBonus+Math.max(0,amount));
    refreshEquipmentHealth(); health=maxHealth;
  }
  public void refreshEquipmentHealth() {
    maxHealth=(int)Math.min(Integer.MAX_VALUE,(long)baseMaxHealth+healthUpgradeBonus+inventory.healthBonus());
    health=Math.min(health,maxHealth);
  }
  public void heal(int amount) {
    if(!defeated() && amount>0) health=(int)Math.min(maxHealth,(long)health+amount);
  }
  public boolean canManageEquipment() {
    return !combat.stunned() && !defeated() && !staggered() && !dashing() && !deathblowing() && !attackRun.running();
  }
  public String defaultAttack() {return inventory.attack(definition.attack);}
  public String comboId() {return inventory.combo(definition.combo);}
  public String animation(String state) {return inventory.animation(state,definition.animations.get(state));}

  public void powerUpgrade() {
    powerUpgrade(2);
  }

  public void powerUpgrade(int amount) {
    attackMultiplier = Math.max(attackMultiplier, amount);
  }

  public int healthDamage() {
    int baseDamage = definition == null ? DAMAGE
        : attackRun.definition() == null ? damageAttack().damage : attackRun.damage();
    return (int) Math.min(Integer.MAX_VALUE, Math.round(baseDamage * (double) attackMultiplier * inventory.damageMultiplier()*combat.damageMultiplier()));
  }

  public double postureDamage() {
    double baseDamage = definition == null ? POSTURE_DAMAGE
        : attackRun.definition() == null ? damageAttack().postureDamage : attackRun.postureDamage();
    return baseDamage * attackMultiplier * inventory.postureMultiplier()*combat.postureMultiplier();
  }

  public int health;
  public boolean facingRight = true, grounded = true;

  public enum Impact {
    NONE,
    DEFLECT,
    BLOCK,
    HIT,
    POSTURE_BREAK,
    DEATHBLOW
  }

  public final Posture posture;
  private final CombatConfig config;
  private boolean guarding, guardSucceeded;
  private int failedTaps;
  private double tapQuietTime, guardWindow;
  private double guardAge, feedbackTime;
  private Impact impact = Impact.NONE;
  private float attackTime, cooldown;
  private boolean hitThisAttack;

  public Player() {
    this(CombatConfig.DEFAULT);
  }

  public Player(CombatConfig config) {
    this(config, DEFAULT_MAX_HEALTH);
  }

  public Player(CombatConfig config, int maxHealth) {
    this(config, maxHealth, null, null);
  }

  public Player(EntityDef definition, Content content) {
    this(definition.combat, definition.maxHealth, definition, content);
  }

  private Player(CombatConfig config, int maxHealth, EntityDef definition, Content content) {
    this.config = config;
    this.definition = definition;
    this.content = content;
    width = definition == null ? WIDTH : definition.width;
    height = definition == null ? HEIGHT : definition.height;
    speed = definition == null ? SPEED : definition.speed;
    gravity = definition == null ? GameWorld.GRAVITY : definition.gravity;
    jumpSpeed = definition == null ? 590 : definition.jumpSpeed;
    climbSpeed = definition == null ? 140 : definition.climbSpeed;
    airSpeed = definition == null ? SPEED : definition.airSpeed;
    acceleration = definition == null ? 0 : definition.acceleration;
    dashDuration = definition == null ? DASH_DURATION : definition.dashDuration;
    dashSpeed = definition == null ? DASH_SPEED : definition.dashSpeed;
    dashCooldownDuration = definition == null ? DASH_COOLDOWN : definition.dashCooldown;
    iframeStart = definition == null ? DASH_IFRAME_START : definition.iframeStart;
    iframeEnd = definition == null ? DASH_IFRAME_END : definition.iframeEnd;
    deathblowDuration = definition == null ? DEATHBLOW_DURATION : definition.deathblowDuration;
    this.baseMaxHealth = Math.max(0, maxHealth);
    this.maxHealth = baseMaxHealth;
    posture = new Posture(config);
    inventory=new Inventory(content,this);
    reset();
  }

  private AttackDef damageAttack() {
    return attackRun.definition() == null
        ? content.attacks.get(defaultAttack())
        : attackRun.definition();
  }

  public float worldWidth() {
    return terrain == null ? GameWorld.WIDTH : terrain.width;
  }

  public float healthFraction() {
    return Vitals.fraction(health, maxHealth);
  }

  public boolean staggered() {
    return posture.broken();
  }

  public double effectiveDeflectWindow() {
    return guarding
        ? guardWindow
        : Math.max(
            config.minimumDeflectWindow, config.deflectWindow - failedTaps * config.spamStep);
  }

  public boolean guarding() {
    return guarding && !combat.stunned() && !staggered() && !defeated() && !dashing() && !deathblowing();
  }

  public boolean dashing() {
    return dashAge < dashDuration - 1e-9;
  }

  public boolean invulnerable() {
    return dashing() && dashAge + 1e-9 >= iframeStart && dashAge < iframeEnd - 1e-9;
  }

  public boolean deathblowing() {
    return deathblowTime > 1e-7;
  }

  public int dashDirection() {
    return dashDirection;
  }

  public double dashCooldownRemaining() {
    return dashCooldown;
  }

  public boolean startDash(int move) {
    if (combat.stunned() || defeated() || dashing() || deathblowing() || dashCooldown > 1e-7) return false;
    if (definition != null
        && !staggered()
        && attackRun.running()
        && !attackRun.definition().canDash(attackRun.age())) return false;
    cancelConfiguredAttack();
    dashDirection = move == 0 ? (facingRight ? -1 : 1) : (move > 0 ? 1 : -1);
    if (move != 0) facingRight = move > 0;
    dashAge = 0;
    dashCooldown = dashCooldownDuration;
    attackTime = 0;
    hitThisAttack = true;
    clearGuard();
    guardNeedsFreshPress = true;
    posture.cancelBreak();
    return true;
  }

  /** Read-only presentation progress; rendering never drives combat timers. */
  public float attackProgress() {
    if (definition != null) return attackRun.progress();
    return Vitals.fraction(ATTACK_DURATION - attackTime, ATTACK_DURATION);
  }

  public boolean attackRecovering() {
    if (definition != null) return attackRun.phase() == AttackRun.Phase.RECOVERY;
    return !attacking() && cooldown > 0 && !dashing() && !deathblowing();
  }

  public float deathblowProgress() {
    return Vitals.fraction(deathblowDuration - deathblowTime, deathblowDuration);
  }

  public float impactProgress() {
    double duration = impact == Impact.DEATHBLOW ? .6 : .25;
    return Vitals.fraction(duration - feedbackTime, duration);
  }

  public Impact impact() {
    return impact;
  }

  private void failTaps(int count) {
    if (count <= 0) return;
    failedTaps = Math.min(1000000, failedTaps + Math.min(count, 1000000));
    tapQuietTime = 0;
  }

  private void finishGuardAttempt() {
    if (guarding && !guardSucceeded && guardAge < config.spamRecovery) {
      failTaps(1);
    }
  }

  public void setGuard(boolean held, boolean pressed) {
    setGuard(held, pressed ? 1 : 0);
  }

  /** Count every press, including short taps completed between render frames. */
  public void setGuard(boolean held, int presses) {
    if (defeated()) {
      clearGuard();
      return;
    }
    if (dashing() || deathblowing()) {
      clearGuard();
      guardNeedsFreshPress = true;
      return;
    }
    presses = Math.max(0, presses);
    if (guardNeedsFreshPress) {
      if (held && presses == 0) {
        guarding = true;
        guardAge = Double.POSITIVE_INFINITY;
        return;
      }
      guardNeedsFreshPress = false;
    }
    if (staggered() || combat.stunned()) {
      guarding = held;
      guardAge = Double.POSITIVE_INFINITY;
      return;
    }
    if (!held) {
      finishGuardAttempt();
      failTaps(presses);
      guarding = false;
      guardAge = 0;
      return;
    }
    if (!guarding || presses > 0) {
      finishGuardAttempt();
      failTaps(Math.max(0, presses - 1));
      guardWindow =
          Math.max(
              config.minimumDeflectWindow, config.deflectWindow - failedTaps * config.spamStep);
      guardAge = 0;
      guardSucceeded = false;
    }
    guarding = true;
  }

  public void clearGuard() {
    guarding = false;
    guardAge = 0;
  }

  public void updateDefense(double dt) {
    if (!Double.isFinite(dt) || dt <= 0) return;
    tapQuietTime += dt;
    if (tapQuietTime + 1e-9 >= config.spamRecovery) failedTaps = 0;
    if (guarding) guardAge += dt;
    posture.update(dt);
    feedbackTime = Math.max(0, feedbackTime - dt);
    if (feedbackTime == 0) impact = Impact.NONE;
  }

  /** Called once by an overlapping active enemy swing, never at windup/activation alone. */
  public void receiveAttack(Enemy attacker) {
    if (defeated() || attacker.defeated() || invulnerable()) return;
    posture.pressure();
    attacker.posture.pressure();
    if (guarding() && attacker.deflectable() && guardAge <= guardWindow + 1e-7) {
      impact = Impact.DEFLECT;
      guardSucceeded = true;
      failedTaps = 0;
      tapQuietTime = 0;
      guardWindow = config.deflectWindow;
      attacker.deflected(
          attacker.definition == null
              ? config.deflectDamage * attacker.damageMultiplier()
              : attacker.deflectDamage()
                  * (definition == null ? 1 : definition.deflectPostureMultiplier));
    } else if (guarding() && attacker.blockable()) {
      impact = Impact.BLOCK;
      addPosture(
          attacker.definition == null
              ? config.blockDamage * attacker.damageMultiplier()
              : attacker.blockDamage()
                  * (definition == null ? 1 : definition.blockPostureMultiplier));
      if (staggered()) {
        impact = Impact.POSTURE_BREAK;
        attackTime = 0;
        guardAge = Double.POSITIVE_INFINITY;
      }
    } else {
      impact = Impact.HIT;
      addPosture(attacker.postureDamage());
      takeDamage(attacker.healthDamage());
      if(!defeated() && canReact(attacker.postureDamage()) && attacker.attackRun.definition()!=null) {
        var rules=attacker.attackRun.definition().combat;combat.react(rules,attacker.facingRight);
        if(rules.vertical!=0){velocityY=rules.vertical;grounded=false;climbing=false;}
        if(combat.stunned())cancelConfiguredAttack();
      }
      if (staggered()) {
        attackTime = 0;
        guardAge = Double.POSITIVE_INFINITY;
      }
    }
    if (staggered()
        || (impact == Impact.HIT
            && definition != null
            && attackRun.running()
            && attackRun.definition().interruptOnHit && canReact(attacker.postureDamage()))) cancelConfiguredAttack();
    feedbackTime = .25;
  }

  public boolean defeated() {
    return health <= 0;
  }

  private void addPosture(double amount){if(!CombatState.staggerImmune(attackRun))posture.add(amount);}
  private boolean canReact(double amount){return !CombatState.armored(attackRun) && !CombatState.staggerImmune(attackRun)
      && amount>=(definition==null?0:definition.staggerResistance);}
  public void takeDamage(int amount) {
    if(CombatState.damageImmune(attackRun))return;
    amount=(int)Math.min(Integer.MAX_VALUE,Math.round(Math.max(0,amount)*inventory.incomingMultiplier()));
    health = (int) Math.max(0, (long) health - Math.max(0, amount));
    if (defeated()) {
      cancelConfiguredAttack();
      attackTime = 0;
      velocityY = 0;
      clearGuard();
      dashAge = dashDuration;
      deathblowTime = 0;
    }
  }

  public void finishTravel() {
    cancelConfiguredAttack();
    attackTime = 0;
    hitThisAttack = true;
    dashAge = dashDuration;
    climbing = false;
    dropTime = 0;
    clearGuard();
    guardNeedsFreshPress = true;
  }

  public void reset() {
    combat.reset();
    attackRun.reset();
    configuredCooldown = 0;
    bufferedCombo = null;
    velocityX = 0;
    healthUpgradeBonus=0;
    inventory.reset();
    maxHealth = baseMaxHealth + inventory.healthBonus();
    attackMultiplier = 1;
    climbing = false;
    dropTime = 0;
    verticalInput = 0;
    x = 180;
    y = GameWorld.GROUND_Y - height;
    velocityY = 0;
    health = maxHealth;
    facingRight = grounded = true;
    attackTime = cooldown = 0;
    hitThisAttack = false;
    clearGuard();
    posture.reset();
    impact = Impact.NONE;
    feedbackTime = 0;
    dashAge = dashDuration;
    dashCooldown = deathblowTime = 0;
    dashDirection = 0;
    guardNeedsFreshPress = false;
    failedTaps = 0;
    tapQuietTime = 0;
    guardWindow = config.deflectWindow;
    guardSucceeded = false;
  }

  public void update(float dt, int move, boolean jump, boolean attack) {
    if (defeated() || !Float.isFinite(dt) || dt <= 0) return;
    float oldX = x;
    x+=combat.tick(dt);
    boolean movingDash = dashing(), executing = deathblowing();
    dashCooldown = Math.max(0, dashCooldown - dt);
    deathblowTime = Math.max(0, deathblowTime - dt);
    attackTime = Math.max(0, attackTime - dt);
    if (definition != null) {
      updateConfiguredAttack(dt, attack);
      x+=(facingRight?1:-1)*attackRun.motionDelta();
    }
    cooldown = Math.max(0, cooldown - dt);
    heal(combat.drainHeal());
    if (staggered() || combat.stunned() || executing) {
      move = 0;
      velocityX = 0;
      jump = attack = false;
      attackTime = 0;
    }
    if (!movingDash && move != 0) facingRight = move > 0;
    if (movingDash) {
      double burst = Math.min(dt, dashDuration - dashAge);
      x += dashDirection * dashSpeed * burst;
      dashAge = Math.min(dashDuration, dashAge + dt);
      x += move * speed * Math.max(0, dt - burst);
      attack = false;
    } else {
      float target = move * (grounded ? speed : airSpeed) * attackRun.movementMultiplier();
      if (acceleration == 0) velocityX = target;
      else
        velocityX +=
            Math.copySign(
                Math.min(Math.abs(target - velocityX), acceleration * dt), target - velocityX);
      x += velocityX * dt;
    }
    if (jump && (grounded || climbing)) {
      velocityY = -jumpSpeed;
      grounded = false;
      climbing = false;
    }
    float oldY = y;
    velocityY += gravity * dt;
    y += velocityY * dt;
    if (terrain == null) CollisionManager.resolveWorld(this);
    else TerrainPhysics.player(this, terrain, oldX, oldY, dt, verticalInput, move, jump);
    if (definition == null && attack && cooldown == 0) {
      attackTime = ATTACK_DURATION;
      cooldown = COOLDOWN;
      hitThisAttack = false;
    }
  }

  public boolean canDeathblow(Enemy enemy) {
    if (defeated()
        || combat.stunned()
        || staggered()
        || dashing()
        || deathblowing()
        || CombatState.damageImmune(enemy.attackRun)
        || enemy.encounterInvulnerable
        || enemy.defeated()
        || !enemy.posture.broken()) return false;
    float dx = enemy.x + enemy.width / 2 - (x + width / 2);
    float gap = Math.abs(dx) - (width + enemy.width) / 2;
    return (facingRight ? dx > 0 : dx < 0)
        && gap <= config.deathblowRange
        && y < enemy.y + enemy.height
        && y + height > enemy.y;
  }

  public boolean tryDeathblow(Enemy enemy) {
    if (!canDeathblow(enemy)) return false;
    cancelConfiguredAttack();
    enemy.takeDamage(enemy.health);
    deathblowTime = deathblowDuration;
    if (definition != null) configuredCooldown = content.attacks.get(defaultAttack()).cooldown;
    clearGuard();
    guardNeedsFreshPress = true;
    attackTime = 0;
    cooldown = COOLDOWN;
    hitThisAttack = true;
    impact = Impact.DEATHBLOW;
    feedbackTime = .6;
    return true;
  }

  public boolean attacking() {
    if (definition != null)
      return attackRun.phase() == AttackRun.Phase.ACTIVE
          && !staggered()
          && !dashing()
          && !deathblowing();
    return attackTime > 0 && !staggered() && !dashing() && !deathblowing();
  }

  public float attackLeft() {
    if (definition != null && attackRun.definition() != null)
      return attackRun.left(x, width, facingRight);
    return facingRight ? x + width : x - ATTACK_RANGE;
  }

  public float attackRight() {
    return attackLeft()
        + (definition != null && attackRun.definition() != null
            ? attackRun.hit().width()
            : ATTACK_RANGE);
  }

  public float attackTop() {
    return y + (definition != null && attackRun.definition() != null ? attackRun.hit().top() : 10);
  }

  public float attackBottom() {
    return definition != null && attackRun.definition() != null
        ? attackTop() + attackRun.hit().height()
        : y + height - 8;
  }

  public void tryHit(Enemy enemy) {
    if (!defeated()
        && attacking()
        && (definition == null ? !hitThisAttack : attackRun.canHit())
        && !enemy.defeated()
        && CollisionManager.overlaps(
            attackLeft(),
            attackTop(),
            attackRight(),
            attackBottom(),
            enemy.x,
            enemy.y,
            enemy.x + enemy.width,
            enemy.y + enemy.height)) {
      posture.pressure();
      enemy.receiveHit(healthDamage(), postureDamage(),attackRun,facingRight);
      hitThisAttack = true;
      if (definition != null) attackRun.consume();
    }
  }

  private void cancelConfiguredAttack() {
    attackRun.cancel();
    bufferedCombo = null;
  }

  private void beginAttack(String id) {
    AttackDef a = content.attacks.get(id);
    attackRun.start(a);
    combat.start(a);
    configuredCooldown = a.cooldown;
  }

  private void updateConfiguredAttack(float dt, boolean pressed) {
    configuredCooldown = Math.max(0, configuredCooldown - dt);
    if (staggered() || combat.stunned() || dashing() || deathblowing()) {
      cancelConfiguredAttack();
      return;
    }
    if (pressed && attackRun.running() && !comboId().isEmpty()) {
      for (var follow : content.combos.get(comboId()).followups)
        if (follow.from().equals(attackRun.definition().id)
            && attackRun.age() + 1e-7 >= follow.open()
            && attackRun.age() <= follow.close() + 1e-7) {
          bufferedCombo = follow;
          break;
        }
    }
    attackRun.advance(dt);
    if (bufferedCombo != null && attackRun.age() + 1e-7 >= bufferedCombo.executeAt()) {
      String next = bufferedCombo.to();
      bufferedCombo = null;
      beginAttack(next);
    } else if (pressed && !attackRun.running() && configuredCooldown <= 1e-7)
      beginAttack(defaultAttack());
  }
}
