package com.lockin.game;

public final class Player {
    public static final float WIDTH = 36, HEIGHT = 64, SPEED = 250;
    public static final float ATTACK_RANGE = 68, ATTACK_DURATION = .16f, COOLDOWN = .45f;
    public float x = 180, y = GameWorld.GROUND_Y - HEIGHT;
    public float velocityY;
    public int health = 100;
    public boolean facingRight = true, grounded = true;
    private float attackTime, cooldown;
    private boolean hitThisAttack;

    public void update(float dt, int move, boolean jump, boolean attack) {
        attackTime = Math.max(0, attackTime - dt);
        cooldown = Math.max(0, cooldown - dt);
        if (move != 0) facingRight = move > 0;
        x += move * SPEED * dt;
        if (jump && grounded) { velocityY = -590; grounded = false; }
        velocityY += GameWorld.GRAVITY * dt;
        y += velocityY * dt;
        CollisionManager.resolveWorld(this);
        if (attack && cooldown == 0) {
            attackTime = ATTACK_DURATION;
            cooldown = COOLDOWN;
            hitThisAttack = false;
        }
    }

    public boolean attacking() { return attackTime > 0; }
    public float attackLeft() { return facingRight ? x + WIDTH : x - ATTACK_RANGE; }
    public float attackRight() { return attackLeft() + ATTACK_RANGE; }
    public void tryHit(Enemy enemy) {
        if (attacking() && !hitThisAttack && !enemy.defeated()
                && CollisionManager.overlaps(attackLeft(), y + 10, attackRight(), y + HEIGHT - 8,
                    enemy.x, enemy.y, enemy.x + Enemy.WIDTH, enemy.y + Enemy.HEIGHT)) {
            enemy.takeDamage(25);
            hitThisAttack = true;
        }
    }
}
