package com.lockin.game;

/** Simulation owned exclusively by the rendering thread. No Android dependencies. */
public final class Game {
    private static final double STEP = 1.0 / 120;
    private double accumulator;
    private long restartVersion;
    public long restartVersion() { return restartVersion; }
    private boolean jumpQueued, attackQueued, dashQueued;
    public final Player player;
    /** Compatibility alias for the first enemy in the current area. */
    public Enemy enemy;
    public final AreaState[] areas = new AreaState[3];
    private int areaIndex;
    private boolean resetPending, complete;
    private float transitionLock;
    private boolean combatArena;
    /** Flat fixture for historical combat regressions; the Android game always uses authored areas. */
    public static Game combatArena() { return combatArena(CombatConfig.DEFAULT); }
    public static Game combatArena(CombatConfig c) {return combatArena(c,Player.DEFAULT_MAX_HEALTH,Enemy.DEFAULT_MAX_HEALTH);}
    public static Game combatArena(CombatConfig c,int p,int e) {Game g=new Game(c,p,e);g.combatArena=true;g.player.terrain=null;return g;}
    public int areaIndex() { return areaIndex; }
    public AreaState area() { return areas[areaIndex]; }
    public Enemy[] enemies() { return area().enemies; }
    public boolean resetPending() { return resetPending; }
    public boolean complete() { return complete; }
    public void requestReset() { resetPending=true; clearQueued(); player.clearGuard(); }
    public void cancelReset() { resetPending=false; clearQueued(); }
    public void confirmReset() { if(resetPending) restart(); }
    private void clearQueued() { accumulator=0; jumpQueued=attackQueued=dashQueued=false; }
    private void enterArea(int index,boolean fromLeft) {
        areaIndex=index; enemy=enemies()[0]; player.terrain=area().definition;
        player.finishTravel();
        player.x=fromLeft?48:GameWorld.WIDTH-Player.WIDTH-48;
        player.y=GameWorld.GROUND_Y-Player.HEIGHT; player.velocityY=0; player.grounded=true; player.climbing=false;
        transitionLock=.4f; restartVersion++; jumpQueued=attackQueued=dashQueued=false;
    }
    private void exits(int move) {
        if(combatArena || transitionLock>0 || player.defeated()) return;
        if(move>0 && player.x>=GameWorld.WIDTH-Player.WIDTH-.01f && player.grounded && player.y+Player.HEIGHT>=GameWorld.GROUND_Y-.1f) {
            if(areaIndex<2) enterArea(areaIndex+1,true); else {complete=true;clearQueued();}
        } else if(move<0 && player.x<=.01f && areaIndex>0 && player.grounded && player.y+Player.HEIGHT>=GameWorld.GROUND_Y-.1f) enterArea(areaIndex-1,false);
    }
    public Game() { this(CombatConfig.DEFAULT); }
    public Game(CombatConfig config) { this(config, Player.DEFAULT_MAX_HEALTH, Enemy.DEFAULT_MAX_HEALTH); }
    public Game(CombatConfig config, int playerMaxHealth, int enemyMaxHealth) {
        player = new Player(config, playerMaxHealth);
        for(int i=0;i<areas.length;i++) areas[i]=new AreaState(AreaDefinition.AREAS[i],config,enemyMaxHealth);
        enemy=enemies()[0]; player.terrain=area().definition;
    }
    public void restart() {
        restartVersion++;
        player.reset(); for(AreaState area:areas) area.reset(); areaIndex=0; enemy=enemies()[0];
        player.terrain=combatArena?null:area().definition; resetPending=complete=false; transitionLock=0; clearQueued();
    }
    public void update(float dt, Controls.Snapshot input) {
        player.verticalInput=input.vertical;
        update(dt,input.move,input.jump,input.attack,input.guard,input.guardPresses,input.dash);
    }
    public void update(float dt, int move, boolean jump, boolean attack) {
        update(dt, move, jump, attack, false, false);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, boolean guardPressed) {
        update(dt, move, jump, attack, guard, guardPressed ? 1 : 0);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, int guardPresses) {
        update(dt, move, jump, attack, guard, guardPresses, false);
    }
    public void update(float dt, int move, boolean jump, boolean attack, boolean guard, int guardPresses, boolean dash) {
        if(resetPending || complete) return;
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
        accumulator += Math.min(dt, .05f);
        while (accumulator + 1e-9 >= STEP) {
            float step = (float) STEP;
            accumulator = Math.max(0, accumulator - STEP);
            for(Enemy e:enemies()) CollisionManager.resolveBodies(player,e);
            transitionLock=Math.max(0,transitionLock-step);
            float oldPlayerX = player.x, oldPlayerY = player.y;
            player.updateDefense(step);
            for(Enemy e:enemies()) e.posture.update(step);
            // Eligible deathblow wins a simultaneous ATTACK/DASH press and cannot be cancelled.
            if(attackQueued) for(Enemy e:enemies()) if(player.tryDeathblow(e)) break;
            if (dashQueued) player.startDash(move);
            player.update(step, Math.max(-1, Math.min(1, move)), jumpQueued, attackQueued);
            player.setGuard(guard, 0);
            for(Enemy e:enemies()) CollisionManager.resolvePlayerMovement(player,e,oldPlayerX,oldPlayerY);
            if(attackQueued) for(Enemy e:enemies()) if(player.tryDeathblow(e)) break;
            jumpQueued = attackQueued = dashQueued = false;
            // Player strikes resolve first: a lethal strike cancels the enemy attack.
            for(Enemy e:enemies()) player.tryHit(e);
            for(int i=0;i<enemies().length;i++) {
                Enemy e=enemies()[i]; float oldX=e.x, oldY=e.y;
                boolean navigating=!combatArena && area().navigators[i].update(e,player,area().definition,step);
                e.update(step,player,navigating);
                if(!combatArena) TerrainPhysics.enemy(e,area().definition,e.climbing?oldY:e.y,step);
                CollisionManager.resolveEnemyMovement(e,player,oldX,oldY);
                for(int j=0;j<enemies().length;j++) if(i!=j) CollisionManager.resolveEnemies(e,enemies()[j],oldX);
                e.tryHit(player);
            }
            exits(move);
            if (player.defeated()) { accumulator = 0; break; }
        }
    }
}
