package com.lockin.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;

public final class GameScreen extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final boolean DEBUG_VISUALS = false;
    private static final String[] BUTTON_LABELS = {"LEFT", "RIGHT", "JUMP", "ATTACK", "GUARD", "DASH"};
    private final CharacterAnimations animations = new CharacterAnimations();
    private final SpritePainter sprites;
    private final String assetError;
    private final Controls.Snapshot controls = new Controls.Snapshot();
    private final Game game = new Game();
    private final InputHandler input = new InputHandler();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean running;
    private boolean resumed, surfaceReady;
    private Thread thread;

    public GameScreen(Context context) {
        super(context);
        SpritePainter loaded = null;
        String failure = null;
        try { loaded = new SpritePainter(context.getAssets()); }
        catch (IOException error) { failure = error.getMessage(); }
        sprites = loaded; assetError = failure;
        getHolder().addCallback(this);
        setFocusable(true);
    }
    public void resume() { resumed = true; startIfReady(); }
    public void pause() { resumed = false; stopLoop(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; startIfReady(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; stopLoop(); }
    // Lifecycle callbacks run on the UI thread; join before any new thread can start.
    private void startIfReady() {
        if (resumed && surfaceReady && thread == null) {
            running = true;
            thread = new Thread(this, "LOCK-IN-render");
            thread.start();
        }
    }
    private void stopLoop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            boolean interrupted = false;
            while (thread.isAlive()) {
                try { thread.join(); } catch (InterruptedException e) { interrupted = true; }
            }
            thread = null;
            if (interrupted) Thread.currentThread().interrupt();
        }
        input.clear();
        game.player.clearGuard();
    }
    @Override public void run() {
        long previous = System.nanoTime();
        final long frameNanos = 1_000_000_000L / 60;
        while (running) {
            long start = System.nanoTime();
            float dt = (start - previous) / 1_000_000_000f;
            previous = start;
            input.consume(controls);
            if (sprites != null) {
                game.update(dt, controls.move, controls.jump, controls.attack, controls.guard, controls.guardPresses, controls.dash);
                animations.update(game, dt);
            }
            Canvas canvas = null;
            try {
                canvas = getHolder().lockCanvas();
                if (canvas != null) drawGame(canvas);
            } finally {
                if (canvas != null) getHolder().unlockCanvasAndPost(canvas);
            }
            long rest = frameNanos - (System.nanoTime() - start);
            if (rest > 0 && running) {
                try { Thread.sleep(rest / 1_000_000, (int) (rest % 1_000_000)); }
                catch (InterruptedException e) { /* Lifecycle stop wakes the pacing sleep. */ }
            }
        }
    }
    private float scale(int width, int height) {
        return Viewport.scale(width, height);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        float s = scale(getWidth(), getHeight());
        if (s > 0) input.onTouch(event, s, (getWidth() - GameWorld.WIDTH * s) / 2,
                (getHeight() - GameWorld.HEIGHT * s) / 2);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private void rect(Canvas c, float l, float t, float r, float b, int color) {
        paint.setColor(color); c.drawRect(l, t, r, b, paint);
    }
    private void text(Canvas c, String value, float x, float y, int size, int color) {
        paint.setColor(color); paint.setTextSize(size); c.drawText(value, x, y, paint);
    }
    private void postureBar(Canvas c, Posture posture, int x, int y, String label) {
        int color = posture.broken() ? Color.RED : Color.YELLOW;
        rect(c, x, y, x + 200, y + 10, Color.DKGRAY);
        rect(c, x, y, x + 200 * posture.fraction(), y + 10, color);
        text(c, posture.broken() ? "POSTURE BROKEN" : label, x, y + 28, 16, color);
    }
    private void outline(Canvas c, float l, float t, float r, float b, int color) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        rect(c, l, t, r, b, color); paint.setStyle(Paint.Style.FILL);
    }
    private void debug(Canvas c) {
        Player p = game.player; Enemy e = game.enemy;
        outline(c, p.x, p.y, p.x + Player.WIDTH, p.y + Player.HEIGHT, Color.CYAN);
        if (!e.defeated()) outline(c, e.x, e.y, e.x + Enemy.WIDTH, e.y + Enemy.HEIGHT, Color.MAGENTA);
        if (p.attacking()) outline(c, p.attackLeft(), p.y + 10, p.attackRight(), p.y + Player.HEIGHT - 8, Color.YELLOW);
        if (e.state() == Enemy.State.ATTACK_ACTIVE)
            outline(c, e.attackLeft(), e.attackTop(), e.attackRight(), e.attackBottom(), Color.RED);
        text(c, "Player " + animations.playerPose() + " / " + (p.dashing() ? "DASH" : p.staggered() ? "STAGGER" : p.attacking() ? "ATTACK" : "MOVE/IDLE"), 24, 195, 16, Color.WHITE);
        text(c, "Enemy " + animations.enemyPose() + " / " + e.state() + " / " + e.move() + " #" + e.strikeNumber(), 24, 216, 16, Color.WHITE);
    }
    private void drawGame(Canvas c) {
        c.drawColor(Color.BLACK);
        float s = scale(c.getWidth(), c.getHeight());
        c.save();
        c.translate((c.getWidth() - GameWorld.WIDTH * s) / 2, (c.getHeight() - GameWorld.HEIGHT * s) / 2);
        c.scale(s, s);
        c.clipRect(0, 0, GameWorld.WIDTH, GameWorld.HEIGHT);
        if (sprites == null) {
            rect(c, 0, 0, 960, 540, Color.rgb(20, 27, 41));
            text(c, "Sprite assets could not be loaded", 24, 200, 26, Color.WHITE);
            text(c, assetError == null ? "Reinstall the complete APK." : assetError, 24, 240, 16, Color.LTGRAY);
            c.restore(); return;
        }
        Player p = game.player; Enemy e = game.enemy;
        sprites.arena(c, p.x + Player.WIDTH / 2);
        paint.setColor(0x55352828);
        c.drawOval(p.x - 6, GameWorld.GROUND_Y - 5, p.x + Player.WIDTH + 6, GameWorld.GROUND_Y + 4, paint);
        c.drawOval(e.x - 6, GameWorld.GROUND_Y - 5, e.x + Enemy.WIDTH + 6, GameWorld.GROUND_Y + 4, paint);
        if (p.dashing()) sprites.character(c, animations.player, p.x + Player.WIDTH / 2 - p.dashDirection() * 28,
                p.y + Player.HEIGHT, p.facingRight, 65);
        sprites.character(c, animations.enemy, e.x + Enemy.WIDTH / 2, e.y + Enemy.HEIGHT, e.facingRight, 255);
        sprites.character(c, animations.player, p.x + Player.WIDTH / 2, p.y + Player.HEIGHT, p.facingRight, p.defeated() ? 180 : 255);
        // Combat effects follow characters. They are cues, never damage or collision inputs.
        if (p.guarding()) {
            float guardX = p.facingRight ? p.x + Player.WIDTH + 3 : p.x - 7;
            rect(c, guardX, p.y + 3, guardX + 4, p.y + Player.HEIGHT - 3, Color.CYAN);
        }
        if (p.impact() != Player.Impact.NONE) {
            int color;
            switch (p.impact()) {
                case DEFLECT: color = Color.YELLOW; break;
                case BLOCK: color = Color.CYAN; break;
                case POSTURE_BREAK: color = Color.MAGENTA; break;
                case DEATHBLOW: color = Color.rgb(255, 100, 60); break;
                default: color = Color.RED; break;
            }
            rect(c, p.x - 5, p.y - 8, p.x + Player.WIDTH + 5, p.y - 3, color);
            text(c, p.impact().name(), Math.max(8, Math.min(770, p.x - 12)), p.y - 20, 18, color);
            // Four short rays around contact give deflects and deathblows a visible burst.
            if (p.impact() == Player.Impact.DEFLECT || p.impact() == Player.Impact.DEATHBLOW) {
                paint.setColor(color); paint.setStrokeWidth(3);
                float cx = p.facingRight ? p.x + Player.WIDTH + 8 : p.x - 8;
                float cy = p.y + 28;
                c.drawLine(cx - 18, cy - 18, cx - 7, cy - 7, paint);
                c.drawLine(cx + 7, cy + 7, cx + 18, cy + 18, paint);
                c.drawLine(cx - 18, cy + 18, cx - 7, cy + 7, paint);
                c.drawLine(cx + 7, cy - 7, cx + 18, cy - 18, paint);
            }
        }
        if (e.state() == Enemy.State.ATTACK_WINDUP) {
            int telegraph = e.move() == EnemyMove.HEAVY_OVERHEAD ? Color.rgb(255, 150, 60) : Color.YELLOW;
            float top = e.y - (e.move() == EnemyMove.HEAVY_OVERHEAD ? 35 : 20);
            rect(c, e.x + 4, top, e.x + Enemy.WIDTH - 4, top + (e.move() == EnemyMove.HEAVY_OVERHEAD ? 12 : 5), telegraph);
            if (e.move() == EnemyMove.DOUBLE_SLASH)
                text(c, e.strikeNumber() == 1 ? "1" : "2", e.x + 15, top - 4, 18, telegraph);
            if (e.move() == EnemyMove.HEAVY_OVERHEAD)
                text(c, "HEAVY", Math.max(8, e.x - 7), top - 6, 16, telegraph);
        }
        if (e.posture.broken() && !e.defeated()) {
            rect(c, e.x - 8, e.y - 42, e.x + Enemy.WIDTH + 8, e.y - 25, Color.RED);
            text(c, "!", e.x + 15, e.y - 27, 18, Color.WHITE);
            rect(c, e.x - 8, e.y - 22, e.x - 8 + (Enemy.WIDTH + 16) * e.posture.breakFraction(), e.y - 18, Color.YELLOW);
        }
        if (!e.defeated()) {
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH, e.y - 7, Color.DKGRAY);
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH * e.healthFraction(), e.y - 7, Color.RED);
        }
        rect(c, 12, 10, 275, 135, 0xBB18212B);
        rect(c, 586, 44, 830, 135, 0xBB18212B);
        rect(c, 315, 10, 950, 42, 0xBB18212B);
        text(c, "LOCK IN  |  v0.3.0", 24, 34, 24, Color.WHITE);
        text(c, "PLAYER " + p.health + "/" + p.maxHealth, 24, 64, 18, Color.WHITE);
        rect(c, 24, 74, 224, 86, Color.DKGRAY);
        rect(c, 24, 74, 24 + 200 * p.healthFraction(), 86, Color.CYAN);
        text(c, "ENEMY " + e.health + "/" + e.maxHealth, 600, 64, 18, Color.WHITE);
        rect(c, 600, 74, 800, 86, Color.DKGRAY);
        rect(c, 600, 74, 600 + 200 * e.healthFraction(), 86, Color.RED);
        postureBar(c, p.posture, 24, 98, "PLAYER POSTURE");
        postureBar(c, e.posture, 600, 98, "ENEMY POSTURE");
        text(c, p.staggered() ? "STAGGERED" : "", 24, 151, 18, Color.RED);
        if (DEBUG_VISUALS) debug(c);
        if (p.defeated()) {
            rect(c, 220, 160, 740, 260, 0xEE141B29);
            text(c, "DEFEATED", 380, 202, 32, Color.RED);
            text(c, "Release JUMP, then tap RESTART", 285, 240, 22, Color.WHITE);
        } else {
            text(c, e.defeated() ? "Enemy defeated!" : p.canDeathblow(e) ? "ATTACK: DEATHBLOW!"
                            : e.posture.broken() ? "Enemy broken: close in and face it!"
                            : "Watch the blade. Guard, deflect or dash.",
                    330, 34, 18, p.canDeathblow(e) ? Color.RED : Color.LTGRAY);
        }
        for (int i = 0; i < Controls.COUNT; i++) {
            int left = Controls.left(i);
            rect(c, left, 425, left + Controls.width(i), 525,
                    i == Controls.GUARD && p.guarding() ? 0xEE236482 : 0xDD182733);
            outline(c, left, 425, left + Controls.width(i), 525, 0xFF9B9280);
            String label = i == Controls.JUMP && p.defeated() ? "RESTART" : BUTTON_LABELS[i];
            text(c, label, left + 8, 482, i == Controls.JUMP && p.defeated() ? 17 : 20, Color.WHITE);
        }
        c.restore();
    }
}
