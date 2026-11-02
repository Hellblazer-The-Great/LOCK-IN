package com.lockin.game;

import com.lockin.game.content.*;
import java.io.*;

/**
 * Classpath asset adapter for the historical JVM fixtures. Android supplies AssetManager instead.
 */
final class FixtureContent {
  private static final class Holder {
    static final Content VALUE = load();

    static Content load() {
      try {
        return Content.load(
            path -> {
              InputStream in = FixtureContent.class.getClassLoader().getResourceAsStream(path);
              if (in == null) throw new IOException("Missing classpath asset " + path);
              return in;
            });
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  static Content get() {
    return Holder.VALUE;
  }

  private FixtureContent() {}
}
