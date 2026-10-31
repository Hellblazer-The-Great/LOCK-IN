package com.lockin.game;

/** Presentation only. Reads simulation state; never deals damage or changes collision/timing. */
public final class CharacterAnimations {
    public enum Pose { IDLE, RUN, JUMP, FALL, ATTACK, GUARD, DEFLECT, DASH, HURT,
        STAGGER, DEATH, DEATHBLOW, WINDUP, ACTIVE, RECOVERY }
    public final AnimationClock player = new AnimationClock(), enemy = new AnimationClock();
    private Pose playerPose = Pose.IDLE, enemyPose = Pose.IDLE;
    private long restart = Long.MIN_VALUE;
    private float previousPlayerX, previousEnemyX;
    private int previousEnemyHealth;
    private double enemyHurt;
    public Pose playerPose() { return playerPose; }
    public Pose enemyPose() { return enemyPose; }
    public void reset(Game game) {
        player.reset(); enemy.reset();
        previousPlayerX = game.player.x; previousEnemyX = game.enemy.x;
        previousEnemyHealth = game.enemy.maxHealth; enemyHurt = 0;
        restart = game.restartVersion();
    }
    private void p(Pose pose, SpriteClip clip, int first, int last, double fps, boolean loop) {
        playerPose = pose; player.play(clip, first, last, fps, loop, pose.ordinal());
    }
    private void e(Pose pose, SpriteClip clip, int first, int last, double fps, boolean loop, int strike) {
        enemyPose = pose; enemy.play(clip, first, last, fps, loop, pose.ordinal() * 10 + strike);
    }
    public void update(Game game, double dt) {
        if (restart != game.restartVersion()) reset(game);
        dt = Double.isFinite(dt) ? Math.max(0, Math.min(.05, dt)) : 0;
        Player actor = game.player;
        boolean running = Math.abs(actor.x - previousPlayerX) > .001f;
        boolean synced = false;
        if (actor.defeated()) p(Pose.DEATH, SpriteClip.NINJA_HURT, 0, 2, 9, false);
        else if (actor.deathblowing()) {
            p(Pose.DEATHBLOW, SpriteClip.NINJA_EXECUTION, 3, 5, 12, false);
            player.sample(actor.deathblowProgress()); synced = true;
        } else if (actor.dashing()) {
            p(Pose.DASH, SpriteClip.NINJA_RUN, 4, 7, 24, true);
        } else if (actor.staggered()) p(Pose.STAGGER, SpriteClip.NINJA_HURT, 0, 2, 9, false);
        else if (actor.attacking()) {
            // Phase 1 attacks hit immediately: start on a striking pose, not a fake windup.
            p(Pose.ATTACK, SpriteClip.NINJA_ATTACK, 3, 5, 12, false);
            player.sample(actor.attackProgress()); synced = true;
        } else if (actor.impact() == Player.Impact.DEFLECT) {
            p(Pose.DEFLECT, SpriteClip.NINJA_GUARD, 0, 3, 12, false);
            player.sample(actor.impactProgress()); synced = true;
        } else if (actor.impact() == Player.Impact.HIT || actor.impact() == Player.Impact.POSTURE_BREAK) {
            p(Pose.HURT, SpriteClip.NINJA_HURT, 0, 2, 12, false);
            player.sample(actor.impactProgress()); synced = true;
        } else if (actor.guarding()) p(Pose.GUARD, SpriteClip.NINJA_GUARD, 0, 7, 12, false);
        else if (!actor.grounded) {
            if (actor.velocityY < 0) p(Pose.JUMP, SpriteClip.NINJA_JUMP, 0, 5, 12, false);
            else p(Pose.FALL, SpriteClip.NINJA_JUMP, 8, 11, 12, false);
        } else if (actor.attackRecovering() && !running) {
            p(Pose.RECOVERY, SpriteClip.NINJA_ATTACK, 5, 5, 12, false);
        } else if (running) p(Pose.RUN, SpriteClip.NINJA_RUN, 0, 7, 12, true);
        else p(Pose.IDLE, SpriteClip.NINJA_IDLE, 0, 7, 12, true);
        if (!synced) player.advance(dt);

        Enemy foe = game.enemy;
        enemyHurt = Math.max(0, enemyHurt - dt);
        if (foe.health < previousEnemyHealth) enemyHurt = .18;
        boolean chasing = Math.abs(foe.x - previousEnemyX) > .001f;
        synced = false;
        if (foe.defeated()) e(Pose.DEATH, SpriteClip.SAMURAI_DEATH, 0, 6, 10, false, 0);
        else if (foe.posture.broken()) e(Pose.STAGGER, SpriteClip.SAMURAI_HURT, 0, 0, 10, false, 0);
        else if (foe.state() == Enemy.State.ATTACK_WINDUP || foe.state() == Enemy.State.ATTACK_ACTIVE
                || foe.state() == Enemy.State.ATTACK_RECOVERY) {
            boolean heavy = foe.move() == EnemyMove.HEAVY_OVERHEAD;
            boolean second = foe.move() == EnemyMove.DOUBLE_SLASH && foe.strikeNumber() == 2;
            SpriteClip clip = heavy ? SpriteClip.SAMURAI_HEAVY : second ? SpriteClip.SAMURAI_RETURN_SLASH : SpriteClip.SAMURAI_SLASH;
            if (foe.state() == Enemy.State.ATTACK_WINDUP)
                e(Pose.WINDUP, clip, heavy ? 0 : second ? 8 : 0, heavy ? 1 : second ? 9 : 3, 12, false, foe.strikeNumber());
            else if (foe.state() == Enemy.State.ATTACK_ACTIVE)
                e(Pose.ACTIVE, clip, heavy ? 2 : second ? 10 : 4, heavy ? 5 : second ? 13 : 7, 12, false, foe.strikeNumber());
            else e(Pose.RECOVERY, clip, heavy ? 6 : second ? 14 : 8, heavy ? 10 : second ? 17 : 9, 12, false, foe.strikeNumber());
            enemy.sample(foe.phaseProgress()); synced = true;
        } else if (enemyHurt > 0) e(Pose.HURT, SpriteClip.SAMURAI_HURT, 0, 1, 12, false, 0);
        else if (chasing) e(Pose.RUN, SpriteClip.SAMURAI_RUN, 0, 5, 12, true, 0);
        else e(Pose.IDLE, SpriteClip.SAMURAI_IDLE, 0, 2, 8, true, 0);
        if (!synced) enemy.advance(dt);
        previousPlayerX = actor.x; previousEnemyX = foe.x; previousEnemyHealth = foe.health;
    }
}
