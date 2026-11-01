package com.lockin.game;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;

public final class GameScreen extends SurfaceView implements SurfaceHolder.Callback, Runnable {
  private static final boolean DEBUG_VISUALS = false;
  private static final String[] BUTTON_LABELS = {
    "LEFT", "RIGHT", "JUMP", "ATTACK", "GUARD", "DASH", "UP", "DOWN"
  };
  private final CharacterAnimations animations = new CharacterAnimations();
  private final CharacterAnimations[] foes = {
    new CharacterAnimations(),
    new CharacterAnimations(),
    new CharacterAnimations(),
    new CharacterAnimations(),
    new CharacterAnimations()
  };
  private final SpritePainter sprites;
  private final String assetError;
  private final Controls.Snapshot controls = new Controls.Snapshot();
  private final Game game = new Game();
  private final Camera camera = new Camera();
  private long cameraRestart = Long.MIN_VALUE;
  private final InputHandler input = new InputHandler();
  private final FrameStats frameStats = new FrameStats();
  private final boolean logTiming;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private volatile boolean running;
  private boolean resumed, surfaceReady;
  private Thread thread;

  public GameScreen(Context context) {
    super(context);
    logTiming = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    SpritePainter loaded = null;
    String failure = null;
    try {
      loaded = new SpritePainter(context.getAssets());
    } catch (IOException error) {
      failure = error.getMessage();
    }
    sprites = loaded;
    assetError = failure;
    getHolder().addCallback(this);
    setFocusable(true);
  }

  public void resume() {
    resumed = true;
    startIfReady();
  }

  public void pause() {
    resumed = false;
    stopLoop();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    surfaceReady = true;
    startIfReady();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    surfaceReady = false;
    stopLoop();
  }

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
        try {
          thread.join();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      thread = null;
      if (interrupted) Thread.currentThread().interrupt();
    }
    input.clear();
    game.player.clearGuard();
  }

  @Override
  public void run() {
    long previous = System.nanoTime();
    final long frameNanos = 1_000_000_000L / 60;
    while (running) {
      long start = System.nanoTime();
      float dt = (start - previous) / 1_000_000_000f;
      previous = start;
      long updateStart = System.nanoTime();
      long beforeSteps = game.simulationSteps();
      double beforeDrop = game.droppedSeconds();
      boolean measuring = !game.resetPending() && !game.complete() && !game.player.defeated();
      input.consume(controls);
      if (sprites != null) {
        int command = input.consumeCommand();
        if (command == 1) game.requestReset();
        else if (command == 2) {
          game.requestReset();
          game.confirmReset();
          input.resetInput();
        } else if (command == 3) {
          game.cancelReset();
          input.resetInput();
        }
        if (!game.resetPending()) {
          game.update(dt, controls);
          double visualDt =
              game.player.defeated() || game.complete()
                  ? Math.min(dt, Game.MAX_BACKLOG_SECONDS)
                  : game.lastAdvanceSeconds();
          if (cameraRestart != game.restartVersion()) {
            camera.reset(game.player, game.area().definition);
            cameraRestart = game.restartVersion();
          } else if (!game.complete() && !game.player.defeated())
            camera.update(game.player, game.area().definition, visualDt);
          animations.updatePlayer(game, visualDt);
          for (int i = 0; i < game.enemies().length; i++)
            foes[i].updateEnemy(game, visualDt, game.enemies()[i]);
        }
      }
      long updateEnd = System.nanoTime();
      Canvas canvas = null;
      try {
        canvas = getHolder().lockCanvas();
        if (canvas != null) drawGame(canvas);
      } finally {
        if (canvas != null) getHolder().unlockCanvasAndPost(canvas);
      }
      long drawEnd = System.nanoTime();
      if (logTiming) {
        if (measuring && !game.resetPending() && game.simulationSteps() >= beforeSteps) {
          frameStats.record(
              dt,
              updateEnd - updateStart,
              drawEnd - updateEnd,
              game.simulationSteps() - beforeSteps,
              game.lastAdvanceSeconds(),
              Math.max(0, game.droppedSeconds() - beforeDrop));
          if (frameStats.ready()) {
            Log.i("LOCK-IN-Timing", frameStats.summary(game.backlogSeconds()));
            frameStats.reset();
          }
        } else frameStats.reset();
      }
      long rest = frameNanos - (System.nanoTime() - start);
      if (rest > 0 && running) {
        try {
          Thread.sleep(rest / 1_000_000, (int) (rest % 1_000_000));
        } catch (InterruptedException e) {
          /* Lifecycle stop wakes the pacing sleep. */
        }
      }
    }
  }

  private float scale(int width, int height) {
    return Viewport.scale(width, height);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    float s = scale(getWidth(), getHeight());
    if (s > 0)
      input.onTouch(
          event,
          s,
          (getWidth() - GameWorld.WIDTH * s) / 2,
          (getHeight() - GameWorld.HEIGHT * s) / 2);
    if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  private void rect(Canvas c, float l, float t, float r, float b, int color) {
    paint.setColor(color);
    c.drawRect(l, t, r, b, paint);
  }

  private void text(Canvas c, String value, float x, float y, int size, int color) {
    paint.setColor(color);
    paint.setTextSize(size);
    c.drawText(value, x, y, paint);
  }

  private void postureBar(Canvas c, Posture posture, int x, int y, String label) {
    int color = posture.broken() ? Color.RED : Color.YELLOW;
    rect(c, x, y, x + 200, y + 10, Color.DKGRAY);
    rect(c, x, y, x + 200 * posture.fraction(), y + 10, color);
    text(c, posture.broken() ? "POSTURE BROKEN" : label, x, y + 28, 16, color);
  }

  private void outline(Canvas c, float l, float t, float r, float b, int color) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(2);
    rect(c, l, t, r, b, color);
    paint.setStyle(Paint.Style.FILL);
  }

  private void debug(Canvas c) {
    Player p = game.player;
    Enemy e = game.enemy;
    outline(c, p.x, p.y, p.x + Player.WIDTH, p.y + Player.HEIGHT, Color.CYAN);
    if (!e.defeated()) outline(c, e.x, e.y, e.x + Enemy.WIDTH, e.y + Enemy.HEIGHT, Color.MAGENTA);
    if (p.attacking())
      outline(c, p.attackLeft(), p.y + 10, p.attackRight(), p.y + Player.HEIGHT - 8, Color.YELLOW);
    if (e.state() == Enemy.State.ATTACK_ACTIVE)
      outline(c, e.attackLeft(), e.attackTop(), e.attackRight(), e.attackBottom(), Color.RED);
    text(
        c,
        "Player "
            + animations.playerPose()
            + " / "
            + (p.dashing()
                ? "DASH"
                : p.staggered() ? "STAGGER" : p.attacking() ? "ATTACK" : "MOVE/IDLE"),
        camera.x() + 24,
        camera.y() + 195,
        16,
        Color.WHITE);
    text(
        c,
        "Enemy "
            + foes[0].enemyPose()
            + " / "
            + e.state()
            + " / "
            + e.move()
            + " #"
            + e.strikeNumber(),
        camera.x() + 24,
        camera.y() + 216,
        16,
        Color.WHITE);
  }

  private void collectible(Canvas c, float x, float feet, int color) {
    if (!camera.visible(x - 24, feet - 72, x + 24, feet)) return;
    float pulse = (float) (.5 + .5 * Math.sin(game.simulationSeconds() * 5));
    rect(c, x - 18, feet - 44, x + 18, feet - 8, (color & 0xFFFFFF) | 0x44000000);
    rect(c, x - 8, feet - 36, x + 8, feet - 20, color);
    paint.setColor(color);
    paint.setStrokeWidth(2);
    float ray = 5 + 5 * pulse;
    c.drawLine(x - ray, feet - 55, x + ray, feet - 55, paint);
    c.drawLine(x, feet - 55 - ray, x, feet - 55 + ray, paint);
  }

  private void drawGame(Canvas c) {
    c.drawColor(Color.BLACK);
    float s = scale(c.getWidth(), c.getHeight());
    c.save();
    c.translate(
        (c.getWidth() - GameWorld.WIDTH * s) / 2, (c.getHeight() - GameWorld.HEIGHT * s) / 2);
    c.scale(s, s);
    c.clipRect(0, 0, GameWorld.WIDTH, GameWorld.HEIGHT);
    if (sprites == null) {
      rect(c, 0, 0, 960, 540, Color.rgb(20, 27, 41));
      text(c, "Sprite assets could not be loaded", 24, 200, 26, Color.WHITE);
      text(
          c,
          assetError == null ? "Reinstall the complete APK." : assetError,
          24,
          240,
          16,
          Color.LTGRAY);
      c.restore();
      return;
    }
    Player p = game.player;
    Enemy e = game.enemy;
    sprites.worldBackground(c, camera.x(), camera.y());
    c.save();
    c.translate(-camera.x(), -camera.y());
    sprites.worldTerrain(c, camera.x(), camera.y());
    if (!game.hpCollected())
      collectible(c, WorldDefinition.HP_X, WorldDefinition.HP_FEET, Color.CYAN);
    if (!game.powerCollected())
      collectible(c, WorldDefinition.POWER_X, WorldDefinition.POWER_FEET, Color.YELLOW);
    paint.setColor(0x55352828);
    c.drawOval(
        p.x - 6, p.y + Player.HEIGHT - 5, p.x + Player.WIDTH + 6, p.y + Player.HEIGHT + 4, paint);
    for (Enemy foe : game.enemies())
      if (camera.visible(foe.x - 240, foe.y - 240, foe.x + 280, foe.y + 240))
        c.drawOval(
            foe.x - 6,
            foe.y + Enemy.HEIGHT - 5,
            foe.x + Enemy.WIDTH + 6,
            foe.y + Enemy.HEIGHT + 4,
            paint);
    if (p.dashing())
      sprites.character(
          c,
          animations.player,
          p.x + Player.WIDTH / 2 - p.dashDirection() * 28,
          p.y + Player.HEIGHT,
          p.facingRight,
          65);
    for (int i = 0; i < game.enemies().length; i++) {
      Enemy foe = game.enemies()[i];
      if (camera.visible(foe.x - 240, foe.y - 240, foe.x + 280, foe.y + 240))
        sprites.character(
            c, foes[i].enemy, foe.x + Enemy.WIDTH / 2, foe.y + Enemy.HEIGHT, foe.facingRight, 255);
    }
    sprites.character(
        c,
        animations.player,
        p.x + Player.WIDTH / 2,
        p.y + Player.HEIGHT,
        p.facingRight,
        p.defeated() ? 180 : 255);
    // Combat effects follow characters. They are cues, never damage or collision inputs.
    if (p.guarding()) {
      float guardX = p.facingRight ? p.x + Player.WIDTH + 3 : p.x - 7;
      rect(c, guardX, p.y + 3, guardX + 4, p.y + Player.HEIGHT - 3, Color.CYAN);
    }
    if (p.impact() != Player.Impact.NONE) {
      int color;
      switch (p.impact()) {
        case DEFLECT:
          color = Color.YELLOW;
          break;
        case BLOCK:
          color = Color.CYAN;
          break;
        case POSTURE_BREAK:
          color = Color.MAGENTA;
          break;
        case DEATHBLOW:
          color = Color.rgb(255, 100, 60);
          break;
        default:
          color = Color.RED;
          break;
      }
      rect(c, p.x - 5, p.y - 8, p.x + Player.WIDTH + 5, p.y - 3, color);
      text(
          c,
          p.impact().name(),
          Math.max(camera.x() + 8, Math.min(camera.x() + 770, p.x - 12)),
          p.y - 20,
          18,
          color);
      // Four short rays around contact give deflects and deathblows a visible burst.
      if (p.impact() == Player.Impact.DEFLECT || p.impact() == Player.Impact.DEATHBLOW) {
        paint.setColor(color);
        paint.setStrokeWidth(3);
        float cx = p.facingRight ? p.x + Player.WIDTH + 8 : p.x - 8;
        float cy = p.y + 28;
        c.drawLine(cx - 18, cy - 18, cx - 7, cy - 7, paint);
        c.drawLine(cx + 7, cy + 7, cx + 18, cy + 18, paint);
        c.drawLine(cx - 18, cy + 18, cx - 7, cy + 7, paint);
        c.drawLine(cx + 7, cy - 7, cx + 18, cy - 18, paint);
      }
    }
    for (Enemy foe : game.enemies()) {
      if (!camera.visible(foe.x - 240, foe.y - 240, foe.x + 280, foe.y + 240)) continue;
      float visualTop =
          foe.y + Enemy.HEIGHT - SpriteClip.SAMURAI_IDLE.anchorY * SpriteClip.SAMURAI_IDLE.scale;
      if (foe.state() == Enemy.State.ATTACK_WINDUP) {
        int telegraph =
            foe.move() == EnemyMove.HEAVY_OVERHEAD ? Color.rgb(255, 150, 60) : Color.YELLOW;
        float top =
            Math.max(
                camera.y() + 78, visualTop - (foe.move() == EnemyMove.HEAVY_OVERHEAD ? 35 : 20));
        float cueX =
            visualTop - camera.y() < 120
                ? (foe.x - camera.x() > 820 ? foe.x - 56 : foe.x + Enemy.WIDTH + 12)
                : foe.x + 4;
        rect(
            c,
            cueX,
            top,
            cueX + Enemy.WIDTH - 8,
            top + (foe.move() == EnemyMove.HEAVY_OVERHEAD ? 12 : 5),
            telegraph);
        if (foe.move() == EnemyMove.DOUBLE_SLASH)
          text(c, foe.strikeNumber() == 1 ? "1" : "2", cueX + 8, top - 4, 18, telegraph);
        if (foe.move() == EnemyMove.HEAVY_OVERHEAD)
          text(c, "HEAVY", Math.max(camera.x() + 8, cueX - 7), top - 6, 16, telegraph);
      }
      if (foe.posture.broken() && !foe.defeated()) {
        float breakX =
            visualTop - camera.y() < 120
                ? (foe.x - camera.x() > 820 ? foe.x - 68 : foe.x + Enemy.WIDTH + 12)
                : foe.x - 8;
        float breakY = Math.max(camera.y() + 78, visualTop - 42);
        rect(c, breakX, breakY, breakX + Enemy.WIDTH + 16, breakY + 17, Color.RED);
        text(c, "!", breakX + 23, breakY + 15, 18, Color.WHITE);
        rect(
            c,
            breakX,
            breakY + 20,
            breakX + (Enemy.WIDTH + 16) * foe.posture.breakFraction(),
            breakY + 24,
            Color.YELLOW);
      }
      if (!foe.defeated()) {
        rect(c, foe.x, visualTop - 14, foe.x + Enemy.WIDTH, visualTop - 7, Color.DKGRAY);
        rect(
            c,
            foe.x,
            visualTop - 14,
            foe.x + Enemy.WIDTH * foe.healthFraction(),
            visualTop - 7,
            Color.RED);
      }
      if (!foe.defeated()) {
        rect(c, foe.x, visualTop - 5, foe.x + Enemy.WIDTH, visualTop - 1, Color.DKGRAY);
        rect(
            c,
            foe.x,
            visualTop - 5,
            foe.x + Enemy.WIDTH * foe.posture.fraction(),
            visualTop - 1,
            Color.YELLOW);
      }
      if (DEBUG_VISUALS) {
        outline(c, foe.x, foe.y, foe.x + Enemy.WIDTH, foe.y + Enemy.HEIGHT, Color.MAGENTA);
        if (foe.state() == Enemy.State.ATTACK_ACTIVE)
          outline(
              c,
              foe.attackLeft(),
              foe.attackTop(),
              foe.attackRight(),
              foe.attackBottom(),
              Color.RED);
        text(c, foe.state().name(), foe.x, visualTop - 50, 12, Color.WHITE);
      }
    }
    if (DEBUG_VISUALS) debug(c);
    c.restore();
    rect(c, 12, 10, 275, 135, 0xBB18212B);
    text(c, "LOCK IN  |  v0.3.2", 24, 34, 24, Color.WHITE);
    text(c, "PLAYER " + p.health + "/" + p.maxHealth, 24, 64, 18, Color.WHITE);
    rect(c, 24, 74, 224, 86, Color.DKGRAY);
    rect(c, 24, 74, 24 + 200 * p.healthFraction(), 86, Color.CYAN);
    int alive = 0;
    for (Enemy foe : game.enemies()) if (!foe.defeated()) alive++;
    rect(c, 590, 10, 945, 42, 0xBB18212B);
    text(c, "ENEMIES: " + alive + " / " + game.enemies().length, 610, 33, 20, Color.WHITE);
    postureBar(c, p.posture, 24, 98, "PLAYER POSTURE");
    text(c, p.staggered() ? "STAGGERED" : "", 24, 151, 18, Color.RED);
    if (!game.notification().isEmpty()) {
      rect(c, 290, 104, 685, 139, 0xDD18212B);
      text(c, game.notification(), 302, 129, 20, Color.YELLOW);
    }
    text(
        c,
        "HP +50: "
            + (game.hpCollected() ? "YES" : "--")
            + "  POWER x2: "
            + (game.powerCollected() ? "YES" : "--"),
        610,
        59,
        16,
        Color.WHITE);
    if (p.defeated()) {
      rect(c, 220, 160, 740, 260, 0xEE141B29);
      text(c, "DEFEATED", 380, 202, 32, Color.RED);
      text(c, "Tap RESET to begin a new run", 270, 240, 22, Color.WHITE);
    }
    if (game.complete()) {
      rect(c, 210, 160, 750, 260, 0xEE141B29);
      text(c, "PROTOTYPE COMPLETE", 270, 215, 30, Color.YELLOW);
      text(c, "RESET to play again", 350, 246, 20, Color.WHITE);
    }
    rect(c, 285, 65, 575, 97, 0xBB18212B);
    text(c, game.regionName(), 295, 87, 20, Color.WHITE);
    rect(c, 420, 10, 540, 58, 0xFF773D35);
    outline(c, 420, 10, 540, 58, Color.WHITE);
    text(c, "RESET", 438, 41, 22, Color.WHITE);
    for (int i = 0; i < Controls.COUNT; i++) {
      int left = Controls.left(i);
      rect(
          c,
          left,
          425,
          left + Controls.width(i),
          525,
          i == Controls.GUARD && p.guarding() ? 0xEE236482 : 0xDD182733);
      outline(c, left, 425, left + Controls.width(i), 525, 0xFF9B9280);
      String label = BUTTON_LABELS[i];
      text(c, label, left + 8, 482, i >= Controls.UP ? 16 : 20, Color.WHITE);
    }
    if (game.resetPending()) {
      rect(c, 0, 0, 960, 540, 0xBB000000);
      rect(c, 220, 165, 740, 350, 0xFF182733);
      text(c, "Reset the entire run?", 305, 210, 28, Color.WHITE);
      text(c, "Enemies, upgrades and progress will reset.", 270, 240, 20, Color.LTGRAY);
      rect(c, 270, 255, 460, 325, 0xFF833D35);
      rect(c, 500, 255, 690, 325, 0xFF356480);
      text(c, "CONFIRM", 310, 299, 22, Color.WHITE);
      text(c, "CANCEL", 545, 299, 22, Color.WHITE);
    }
    c.restore();
  }
}
