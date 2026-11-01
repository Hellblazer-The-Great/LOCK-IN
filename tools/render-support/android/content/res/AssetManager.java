package android.content.res;

import java.io.*;
import java.nio.file.*;

/** Host renderer probe only, never packaged in the APK. */
public final class AssetManager {
  public InputStream open(String path) throws IOException {
    return Files.newInputStream(Path.of("app/src/main/assets", path));
  }
}
