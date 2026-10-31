package com.lockin.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public final class GameScreen extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final boolean DEBUG_HITBOXES = false;
    private final Controls.Snapshot controls = new Controls.Snapshot();
    private final Game game = new Game();
    private final InputHandler input = new InputHandler();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean running;
    private boolean resumed, surfaceReady;
    private Thread thread;

    public GameScreen(Context context) {
        super(context);
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
            game.update(dt, controls.move, controls.jump, controls.attack, controls.guard, controls.guardPresses, controls.dash);
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
        return Math.min(width / GameWorld.WIDTH, height / GameWorld.HEIGHT);
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
    private int enemyColor(Enemy.State state) {
        switch (state) {
            case POSTURE_BROKEN: return Color.rgb(255, 220, 180);
            case CHASE: return Color.rgb(230, 130, 65);
            case ATTACK_WINDUP: return Color.YELLOW;
            case ATTACK_ACTIVE: return Color.RED;
            case ATTACK_RECOVERY: return Color.rgb(160, 100, 210);
            default: return Color.rgb(209, 83, 94);
        }
    }
    private void drawGame(Canvas c) {
        c.drawColor(Color.BLACK);
        float s = scale(c.getWidth(), c.getHeight());
        c.save();
        c.translate((c.getWidth() - GameWorld.WIDTH * s) / 2, (c.getHeight() - GameWorld.HEIGHT * s) / 2);
        c.scale(s, s);
        rect(c, 0, 0, 960, 540, Color.rgb(20, 27, 41));
        rect(c, 0, GameWorld.GROUND_Y, 960, 400, Color.rgb(90, 105, 112));
        Player p = game.player;
        if (p.attacking()) rect(c, p.attackLeft(), p.y + 10, p.attackRight(), p.y + Player.HEIGHT - 8, 0x99FFBB55);
        if (p.dashing()) {
            float trail = p.x - p.dashDirection() * 28;
            rect(c, trail, p.y + 5, trail + Player.WIDTH, p.y + Player.HEIGHT - 5, 0x5552BFE2);
        }
        rect(c, p.x, p.y, p.x + Player.WIDTH, p.y + Player.HEIGHT,
                p.invulnerable() ? Color.WHITE : p.dashing() ? Color.CYAN : Color.rgb(82, 191, 226));
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
        float eye = p.facingRight ? p.x + 26 : p.x + 5;
        rect(c, eye, p.y + 12, eye + 5, p.y + 17, Color.WHITE);
        Enemy e = game.enemy;
        if (!e.defeated()) {
            rect(c, e.x, e.y, e.x + Enemy.WIDTH, e.y + Enemy.HEIGHT, enemyColor(e.state()));
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH, e.y - 7, Color.DKGRAY);
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH * e.healthFraction(), e.y - 7, Color.RED);
        }
        text(c, "LOCK IN  |  v0.2.4", 24, 34, 24, Color.WHITE);
        text(c, "PLAYER " + p.health + "/" + p.maxHealth, 24, 64, 18, Color.WHITE);
        rect(c, 24, 74, 224, 86, Color.DKGRAY);
        rect(c, 24, 74, 24 + 200 * p.healthFraction(), 86, Color.CYAN);
        text(c, "ENEMY " + e.health + "/" + e.maxHealth, 600, 64, 18, Color.WHITE);
        rect(c, 600, 74, 800, 86, Color.DKGRAY);
        rect(c, 600, 74, 600 + 200 * e.healthFraction(), 86, Color.RED);
        postureBar(c, p.posture, 24, 98, "PLAYER POSTURE");
        postureBar(c, e.posture, 600, 98, "ENEMY POSTURE");
        text(c, p.staggered() ? "STAGGERED" : "", 24, 151, 18, Color.RED);
        text(c, e.state().name(), 600, 151, 16, Color.LTGRAY);
        if (!e.defeated()) text(c, e.move().name() + " " + e.strikeNumber() + "/" + e.move().strikes,
                600, 174, 16, e.move() == EnemyMove.HEAVY_OVERHEAD ? Color.rgb(255, 150, 60) : Color.LTGRAY);
        if (e.state() == Enemy.State.ATTACK_WINDUP) {
            int telegraph = e.move() == EnemyMove.HEAVY_OVERHEAD ? Color.rgb(255, 150, 60) : Color.YELLOW;
            float top = e.y - (e.move() == EnemyMove.HEAVY_OVERHEAD ? 35 : 20);
            rect(c, e.x + 4, top, e.x + Enemy.WIDTH - 4, top + (e.move() == EnemyMove.HEAVY_OVERHEAD ? 12 : 5), telegraph);
            if (e.move() == EnemyMove.DOUBLE_SLASH)
                text(c, Integer.toString(e.strikeNumber()), e.x + 15, top - 4, 18, telegraph);
        }
        if (e.posture.broken() && !e.defeated()) {
            rect(c, e.x - 8, e.y - 42, e.x + Enemy.WIDTH + 8, e.y - 25, Color.RED);
            text(c, "!", e.x + 15, e.y - 27, 18, Color.WHITE);
            rect(c, e.x - 8, e.y - 22, e.x - 8 + (Enemy.WIDTH + 16) * e.posture.breakFraction(), e.y - 18, Color.YELLOW);
        }
        if (e.state() == Enemy.State.ATTACK_ACTIVE) {
            // Bright body-edge flash marks each active strike even with debug hitboxes hidden.
            rect(c, e.x - 3, e.y, e.x, e.y + Enemy.HEIGHT,
                    e.move() == EnemyMove.HEAVY_OVERHEAD ? Color.rgb(255, 150, 60) : Color.RED);
            if (DEBUG_HITBOXES) rect(c, e.attackLeft(), e.attackTop(), e.attackRight(), e.attackBottom(), 0xAAFF4444);
        }
        if (!e.defeated()) {
            float enemyEye = e.facingRight ? e.x + Enemy.WIDTH - 10 : e.x + 5;
            rect(c, enemyEye, e.y + 12, enemyEye + 5, e.y + 17, Color.WHITE);
        }
        if (p.defeated()) {
            rect(c, 220, 160, 740, 260, 0xEE141B29);
            text(c, "DEFEATED", 380, 202, 32, Color.RED);
            text(c, "Release JUMP, then tap RESTART", 285, 240, 22, Color.WHITE);
        } else {
            text(c, e.defeated() ? "Enemy defeated!" : p.canDeathblow(e) ? "ATTACK: DEATHBLOW!"
                            : e.posture.broken() ? "Enemy broken: close in and face it!"
                            : "Yellow: windup | Red: strike | Purple: recovery",
                    330, 34, 18, p.canDeathblow(e) ? Color.RED : Color.LTGRAY);
        }
        String[] labels = {"LEFT", "RIGHT", p.defeated() ? "RESTART" : "JUMP", "ATTACK", "GUARD", "DASH"};
        for (int i = 0; i < Controls.COUNT; i++) {
            int left = Controls.left(i);
            rect(c, left, 425, left + Controls.width(i), 525,
                    i == Controls.GUARD && p.guarding() ? Color.rgb(35, 100, 130) : Color.rgb(48, 63, 81));
            text(c, labels[i], left + 8, 482, i == Controls.JUMP && p.defeated() ? 17 : 20, Color.WHITE);
        }
        c.restore();
    }
}
