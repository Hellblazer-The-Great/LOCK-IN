package com.lockin.game;

/** Verified PNG layouts, not the occasionally stale pack README/Aseprite frame counts.
 * Anchors are fixed art-space feet/body centers; transparent padding never defines physics. */
public enum SpriteClip {
    NINJA_IDLE("DarkNinja/frames/Idle/%02d.png", 64, 64, 8, 8, 30, 56, 1.6f, true),
    NINJA_RUN("DarkNinja/frames/Move/%02d.png", 64, 64, 8, 8, 30, 56, 1.6f, true),
    NINJA_JUMP("DarkNinja/frames/Jump/%02d.png", 64, 64, 12, 12, 30, 56, 1.6f, true),
    NINJA_ATTACK("DarkNinja/frames/Attack1/%02d.png", 96, 64, 6, 6, 30, 56, 1.6f, true),
    NINJA_EXECUTION("DarkNinja/frames/Attack2/%02d.png", 96, 64, 6, 6, 30, 56, 1.6f, true),
    NINJA_GUARD("DarkNinja/frames/Defend/%02d.png", 64, 64, 8, 8, 30, 56, 1.6f, true),
    NINJA_HURT("DarkNinja/frames/Hurt/%02d.png", 64, 64, 3, 3, 30, 56, 1.6f, true),
    SAMURAI_IDLE("Samurai_Bundle/Samurai/Idle/Idle_1.png", 16, 16, 3, 2, 8, 14, 5, false),
    SAMURAI_RUN("Samurai_Bundle/Samurai/Run/Samurai_Run.png", 16, 16, 6, 3, 8, 14, 5, false),
    SAMURAI_SLASH("Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png", 48, 48, 18, 5, 21, 32, 5, false),
    // The return swing in this sheet faces the opposite native direction; share the cached bitmap.
    SAMURAI_RETURN_SLASH("Samurai_Bundle/Samurai/Attack/Attack_1/Samurai_Attack_1.png", 48, 48, 18, 5, 21, 32, 5, true),
    SAMURAI_HEAVY("Samurai_Bundle/Samurai/Attack/Attack_Down/Samurai_Attack_Down.png", 48, 32, 11, 4, 24, 13, 5, false),
    SAMURAI_HURT("Samurai_Bundle/Samurai/Hit/Samurai_Hit.png", 16, 16, 2, 2, 8, 16, 5, false),
    SAMURAI_DEATH("Samurai_Bundle/Samurai/Death/Samurai_Death_1.png", 32, 16, 7, 3, 8, 16, 5, false);

    public final String asset;
    public final int frameWidth, frameHeight, frames, columns;
    public final float anchorX, anchorY, scale;
    public final boolean facesRight;
    SpriteClip(String asset, int width, int height, int frames, int columns,
               float anchorX, float anchorY, float scale, boolean facesRight) {
        this.asset = "sprites/" + asset;
        frameWidth = width; frameHeight = height; this.frames = frames; this.columns = columns;
        this.anchorX = anchorX; this.anchorY = anchorY; this.scale = scale; this.facesRight = facesRight;
    }
    public boolean sequence() { return asset.contains("%02d"); }
    public int sourceX(int frame) { return (frame % columns) * frameWidth; }
    public int sourceY(int frame) { return (frame / columns) * frameHeight; }
    public boolean flip(boolean facingRight) { return facingRight != facesRight; }
}
