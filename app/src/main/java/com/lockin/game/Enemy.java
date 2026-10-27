package com.lockin.game;

public final class Enemy {
    public static final float WIDTH = 44, HEIGHT = 70;
    public final float x = 670, y = GameWorld.GROUND_Y - HEIGHT;
    public int health = 100;
    public void takeDamage(int amount) { health = Math.max(0, health - Math.max(0, amount)); }
    public boolean defeated() { return health == 0; }
}
