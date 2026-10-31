package com.lockin.game;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Host-only image decoding and production presentation-controller checks. No Android runtime. */
public final class VisualChecks {
    private static final float DT = 1f / 120;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void tick(Game game, CharacterAnimations animations, int move, boolean jump, boolean attack) {
        game.update(DT, move, jump, attack); animations.update(game, DT);
    }
    private static void phase(Enemy enemy, Player player, Enemy.State state, int strike) {
        int count = 0;
        while (!(enemy.state() == state && enemy.strikeNumber() == strike) && count++ < 5000) enemy.update(DT, player);
        check(enemy.state() == state && enemy.strikeNumber() == strike, "reaches animation combat phase");
    }
    private static Game move(EnemyMove wanted) {
        Game game = new Game(new CombatConfig(.25, 10000, 0, 25, 35, 1, 1), 10000, 10000);
        game.player.x = game.enemy.x - Player.WIDTH - 20;
        int count = 0;
        while (!(game.enemy.move() == wanted && game.enemy.state() == Enemy.State.ATTACK_WINDUP) && count++ < 5000)
            game.enemy.update(DT, game.player);
        check(game.enemy.move() == wanted, "prepares move animation"); return game;
    }
    public static void main(String[] args) throws Exception {
        AnimationClock clock = new AnimationClock();
        clock.play(SpriteClip.NINJA_IDLE, 0, 7, 12, true, 0);
        clock.advance(.5); check(clock.frame() == 6, "configurable loop playback speed");
        clock.play(SpriteClip.NINJA_IDLE, 0, 7, 12, true, 0);
        check(clock.frame() == 6, "same state does not restart playback");
        clock.advance(1.0 / 6); check(clock.frame() == 0, "loop wraps");
        clock.play(SpriteClip.SAMURAI_DEATH, 0, 6, 10, false, 1);
        clock.advance(10); check(clock.frame() == 6, "one-shot holds last nonblank death frame");
        clock.play(SpriteClip.SAMURAI_DEATH, 0, 6, 10, false, 2);
        check(clock.frame() == 0, "new state token restarts one-shot");
        clock.play(SpriteClip.SAMURAI_SLASH, 4, 7, 12, false, 3);
        clock.sample(0); check(clock.frame() == 4, "phase begins on first striking frame");
        clock.sample(.5); check(clock.frame() == 6, "phase sampling follows combat progress");
        clock.sample(1); check(clock.frame() == 7, "phase endpoint cannot sample outside range");
        clock.sample(-1); check(clock.frame() == 4, "negative progress clamps");
        clock.sample(Double.NaN); check(clock.frame() == 4, "invalid progress safe");
        clock.advance(Double.NaN); check(clock.frame() == 4, "invalid elapsed time ignored");
        for (int fps : new int[]{30, 60, 120, 144, 240}) {
            clock.reset(); clock.play(SpriteClip.NINJA_RUN, 0, 7, 12, true, 0);
            for (int i = 0; i < fps; i++) clock.advance(1.0 / fps);
            check(clock.frame() == 4, "loop playback independent of frame rate " + fps);
        }
        for (SpriteClip clip : SpriteClip.values()) {
            check(!clip.flip(clip.facesRight) && clip.flip(!clip.facesRight), "native orientation and flip " + clip);
            check(clip.anchorX >= 0 && clip.anchorX < clip.frameWidth && clip.anchorY <= clip.frameHeight,
                    "fixed body/feet anchor inside frame " + clip);
            if (clip.sequence()) {
                for (int frame = 0; frame < clip.frames; frame++) {
                    BufferedImage image = image(String.format(Locale.ROOT, clip.asset, frame + 1));
                    check(image.getWidth() == clip.frameWidth && image.getHeight() == clip.frameHeight,
                            "actual sequence dimensions " + clip + " " + frame);
                    check(visible(image, 0, 0, clip.frameWidth, clip.frameHeight), "nonblank sequence frame");
                }
            } else {
                BufferedImage image = image(clip.asset);
                check(image.getWidth() == clip.columns * clip.frameWidth && image.getHeight() % clip.frameHeight == 0,
                        "actual sheet grid " + clip);
                for (int frame = 0; frame < clip.frames; frame++) {
                    int x = clip.sourceX(frame), y = clip.sourceY(frame);
                    check(x + clip.frameWidth <= image.getWidth() && y + clip.frameHeight <= image.getHeight(), "source rect inside sheet");
                    check(visible(image, x, y, clip.frameWidth, clip.frameHeight), "selected sheet cell is not blank " + clip + " " + frame);
                }
            }
        }
        for (int i = 1; i <= 5; i++) {
            BufferedImage image = image("sprites/Samurai_Bundle/Map/Zen_Island/Background_" + i + ".png");
            check(image.getWidth() == 192 && image.getHeight() == 108, "actual background layer " + i);
        }
        BufferedImage tiles = image("sprites/Samurai_Bundle/Map/Zen_Island/Tileset_1.png");
        check(visible(tiles, 8, 0, 8, 24) && visible(tiles, 48, 4, 16, 20), "ground/tree atlas regions exist");
        for (int[] size : new int[][]{{960,540},{1920,1080},{2340,1080},{2560,1600},{1024,600}}) {
            float scale = Viewport.scale(size[0],size[1]);
            float ox = (size[0] - 960 * scale)/2, oy=(size[1]-540*scale)/2;
            check(ox >= -.001 && oy >= -.001 && ox+960*scale <= size[0]+.001 && oy+540*scale <= size[1]+.001,
                    "landscape world fits without aspect distortion " + size[0]);
            float spriteWidth = SpriteClip.NINJA_IDLE.frameWidth * SpriteClip.NINJA_IDLE.scale * scale;
            float spriteHeight = SpriteClip.NINJA_IDLE.frameHeight * SpriteClip.NINJA_IDLE.scale * scale;
            check(Math.abs(spriteWidth / spriteHeight - 1) < .001, "sprite aspect ratio preserved");
        }
        check(Viewport.scale(0,0) == 0, "empty viewport safe");

        Game game = new Game(); CharacterAnimations animations = new CharacterAnimations(); animations.update(game, 0);
        check(animations.playerPose() == CharacterAnimations.Pose.IDLE && animations.enemyPose() == CharacterAnimations.Pose.IDLE, "initial idle poses");
        for (int i = 0; i < 10; i++) tick(game, animations, 1, false, false);
        check(animations.playerPose() == CharacterAnimations.Pose.RUN && animations.player.frame() > 0, "running advances without restarting each frame");
        tick(game, animations, 0, true, false);
        check(animations.playerPose() == CharacterAnimations.Pose.JUMP, "rising jump pose");
        while (game.player.velocityY < 0) tick(game, animations, 0, false, false);
        check(animations.playerPose() == CharacterAnimations.Pose.FALL, "falling transitions to landing frames");
        while (!game.player.grounded) tick(game, animations, 0, false, false);
        check(animations.playerPose() == CharacterAnimations.Pose.IDLE, "landing returns to idle");
        game.player.setGuard(true, true); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.GUARD, "held guard animation");
        game.player.receiveAttack(game.enemy); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.DEFLECT, "deflect uses defensive frames");
        game.player.startDash(1); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.DASH, "dash overrides old deflect feedback");
        game.player.update(.18f, 0, false, false); game.player.setGuard(false, false); game.player.posture.add(100);
        animations.update(game, DT); check(animations.playerPose() == CharacterAnimations.Pose.STAGGER, "posture break shows recoil");
        game.player.update(.3f, 0, false, false); game.player.startDash(-1); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.DASH, "stagger cancellation immediately selects dash animation");
        game.restart(); animations.update(game, 0);
        check(animations.playerPose() == CharacterAnimations.Pose.IDLE && animations.player.frame() == 0
                && animations.enemyPose() == CharacterAnimations.Pose.IDLE && animations.enemy.frame() == 0, "restart clears all visual state");
        game.player.update(DT, 0, false, true); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.ATTACK && animations.player.frame() >= 3,
                "immediate player active hit starts on striking frame");
        game.player.startDash(-1); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.DASH, "attack cancellation immediately removes attack animation");
        game.restart(); game.enemy.takeDamage(1); animations.update(game, DT);
        check(animations.enemyPose() == CharacterAnimations.Pose.HURT, "idle enemy damage uses hurt sheet");
        game.enemy.takeDamage(1000); animations.update(game, DT);
        check(animations.enemyPose() == CharacterAnimations.Pose.DEATH, "enemy death overrides hurt");
        for (int i=0;i<150;i++) animations.update(game,DT);
        check(animations.enemy.frame() == 6, "enemy corpse holds last real death frame");
        game.restart(); game.player.x = game.enemy.x - Player.WIDTH - 20; game.enemy.deflected(100);
        game.player.tryDeathblow(game.enemy); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.DEATHBLOW && animations.enemyPose() == CharacterAnimations.Pose.DEATH,
                "execution and enemy death have priority over ordinary states");
        check(animations.player.frame() >= 3, "instant deathblow damage begins on an execution strike pose");
        game.player.takeDamage(1000); animations.update(game, DT);
        check(animations.playerPose() == CharacterAnimations.Pose.DEATH, "player defeat reuses hurt safely");

        for (EnemyMove move : EnemyMove.values()) {
            game = move(move); animations = new CharacterAnimations(); animations.update(game, 0);
            check(animations.enemyPose() == CharacterAnimations.Pose.WINDUP, "preparation visible before " + move);
            check(animations.enemy.frame() <= (move == EnemyMove.HEAVY_OVERHEAD ? 1 : 3), "no striking frame in first windup");
            SpriteClip expected = move == EnemyMove.HEAVY_OVERHEAD ? SpriteClip.SAMURAI_HEAVY : SpriteClip.SAMURAI_SLASH;
            check(animations.enemy.clip() == expected, "move uses inspected source clip");
            phase(game.enemy, game.player, Enemy.State.ATTACK_ACTIVE, 1); animations.update(game, 0);
            check(animations.enemyPose() == CharacterAnimations.Pose.ACTIVE
                    && animations.enemy.frame() == (move == EnemyMove.HEAVY_OVERHEAD ? 2 : 4), "active starts on first striking frame");
            int hp = game.player.health; game.enemy.tryHit(game.player);
            check(game.player.health < hp, "combat hit occurs independently of sprite draw");
            if (move == EnemyMove.DOUBLE_SLASH) {
                phase(game.enemy, game.player, Enemy.State.ATTACK_WINDUP, 2); animations.update(game, 0);
                check(animations.enemy.frame() == 8, "double second preparation is visually distinct");
                phase(game.enemy, game.player, Enemy.State.ATTACK_ACTIVE, 2); animations.update(game, 0);
                check(animations.enemy.frame() == 10, "double second hit has its own striking range");
                check(animations.enemy.clip() == SpriteClip.SAMURAI_RETURN_SLASH
                        && animations.enemy.clip().flip(game.enemy.facingRight),
                        "native return-swing direction is corrected toward the player");
                hp = game.player.health; game.enemy.tryHit(game.player); check(game.player.health < hp, "second hit still independent");
            }
            phase(game.enemy, game.player, Enemy.State.ATTACK_RECOVERY, move.strikes); animations.update(game, 0);
            check(animations.enemyPose() == CharacterAnimations.Pose.RECOVERY, "follow-through follows actual recovery");
            int frame = animations.enemy.frame(); animations.update(game, .05);
            check(animations.enemy.frame() == frame, "attack visuals cannot advance while combat phase is paused");
        }
        game = new Game(); int hp = game.player.health, enemyHp = game.enemy.health; float x = game.player.x;
        animations = new CharacterAnimations();
        for(int i=0;i<1000;i++) animations.update(game,DT);
        check(game.player.health == hp && game.enemy.health == enemyHp && game.player.x == x,
                "render controller cannot cause damage or motion");
        check(Player.WIDTH == 36 && Player.HEIGHT == 64 && Enemy.WIDTH == 44 && Enemy.HEIGHT == 70,
                "different art sizes leave physical dimensions intact");
        System.out.println("Passed " + checks + " animation/asset checks");
    }
    private static BufferedImage image(String asset) throws Exception {
        Path file = Path.of("app/src/main/assets", asset);
        check(Files.isRegularFile(file), "selected asset exists: " + asset);
        BufferedImage image = ImageIO.read(file.toFile());
        check(image != null, "selected asset decodes: " + asset); return image;
    }
    private static boolean visible(BufferedImage image,int x,int y,int w,int h) {
        for(int row=y;row<y+h;row++) for(int col=x;col<x+w;col++) if((image.getRGB(col,row)>>>24)!=0)return true;
        return false;
    }
}
