package com.lockin.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public final class GameScreen extends SurfaceView implements SurfaceHolder.Callback, Runnable {
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
    }
    @Override public void run() {
        long previous = System.nanoTime();
        final long frameNanos = 1_000_000_000L / 60;
        while (running) {
            long start = System.nanoTime();
            float dt = (start - previous) / 1_000_000_000f;
            previous = start;
            int[] controls = input.consume();
            game.update(dt, controls[0], controls[1] != 0, controls[2] != 0);
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
    private int enemyColor(Enemy.State state) {
        switch (state) {
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
        rect(c, p.x, p.y, p.x + Player.WIDTH, p.y + Player.HEIGHT, Color.rgb(82, 191, 226));
        float eye = p.facingRight ? p.x + 26 : p.x + 5;
        rect(c, eye, p.y + 12, eye + 5, p.y + 17, Color.WHITE);
        Enemy e = game.enemy;
        if (!e.defeated()) {
            rect(c, e.x, e.y, e.x + Enemy.WIDTH, e.y + Enemy.HEIGHT, enemyColor(e.state()));
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH, e.y - 7, Color.DKGRAY);
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH * e.health / 100f, e.y - 7, Color.RED);
        }
        text(c, "LOCK IN  |  Phase 2A", 24, 34, 24, Color.WHITE);
        text(c, "PLAYER " + p.health, 24, 64, 18, Color.WHITE);
        rect(c, 24, 74, 224, 86, Color.DKGRAY);
        rect(c, 24, 74, 24 + 2 * p.health, 86, Color.CYAN);
        text(c, "ENEMY " + e.health + "  " + e.state(), 600, 64, 18, Color.WHITE);
        rect(c, 600, 74, 800, 86, Color.DKGRAY);
        rect(c, 600, 74, 600 + 2 * e.health, 86, Color.RED);
        if (e.state() == Enemy.State.ATTACK_ACTIVE) {
            rect(c, e.attackLeft(), e.y + 10, e.attackRight(), e.y + Enemy.HEIGHT - 8, 0xAAFF4444);
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
            text(c, e.defeated() ? "Enemy defeated!" : "Yellow: windup | Red: strike | Purple: recovery",
                    330, 34, 18, Color.LTGRAY);
        }
        int[] lefts = {20, 140, 700, 820};
        String[] labels = {"LEFT", "RIGHT", p.defeated() ? "RESTART" : "JUMP", "ATTACK"};
        for (int i = 0; i < 4; i++) {
            rect(c, lefts[i], 425, lefts[i] + (i == 3 ? 120 : 100), 525, Color.rgb(48, 63, 81));
            text(c, labels[i], lefts[i] + 8, 482, i == 2 && p.defeated() ? 17 : 20, Color.WHITE);
        }
        c.restore();
    }
}
