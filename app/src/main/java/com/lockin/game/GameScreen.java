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
            rect(c, e.x, e.y, e.x + Enemy.WIDTH, e.y + Enemy.HEIGHT, Color.rgb(209, 83, 94));
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH, e.y - 7, Color.DKGRAY);
            rect(c, e.x, e.y - 14, e.x + Enemy.WIDTH * e.health / 100f, e.y - 7, Color.RED);
        }
        text(c, "LOCK IN  |  Phase 1", 24, 34, 24, Color.WHITE);
        text(c, "HP " + p.health + "   Target " + e.health, 24, 65, 20, Color.WHITE);
        text(c, e.defeated() ? "Target defeated! Force-stop app to reset." : "Move close. Face the target. Strike.", 330, 34, 20, Color.LTGRAY);
        int[] lefts = {20, 140, 700, 820};
        String[] labels = {"LEFT", "RIGHT", "JUMP", "ATTACK"};
        for (int i = 0; i < 4; i++) {
            rect(c, lefts[i], 425, lefts[i] + (i == 3 ? 120 : 100), 525, Color.rgb(48, 63, 81));
            text(c, labels[i], lefts[i] + 12, 482, 20, Color.WHITE);
        }
        c.restore();
    }
}
