package com.lockin.game;

import com.lockin.game.content.*;
import java.io.*;
import java.nio.file.*;

public final class ValidateContent {
  public static ContentSource files(Path root) {
    return path -> {
      Path file = root.resolve(path).normalize();
      if (!file.startsWith(root.normalize()))
        throw new IOException("Path outside content root: " + path);
      return Files.newInputStream(file);
    };
  }

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args.length == 0 ? "app/src/main/assets" : args[0]);
    Content c = Content.load(files(root));
    Game game = new Game(c);
    System.out.println(
        "Content valid: "
            + c.levels.size()
            + " level(s), "
            + c.entities.size()
            + " entities, "
            + c.attacks.size()
            + " attacks, "
            + c.animations.size()
            + " animations; start="
            + game.level().id
            + ", enemies="
            + game.enemies().length);
  }
}
