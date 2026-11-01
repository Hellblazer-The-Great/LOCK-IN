package android.graphics;

import java.io.*;
import javax.imageio.ImageIO;

public final class BitmapFactory {
  public static int decodes;

  public static class Options {
    public boolean inScaled;
    public Bitmap.Config inPreferredConfig;
  }

  public static Bitmap decodeStream(InputStream stream, Object padding, Options options) {
    decodes++;
    try {
      var image = ImageIO.read(stream);
      return image == null ? null : new Bitmap(image.getWidth(), image.getHeight());
    } catch (IOException e) {
      return null;
    }
  }
}
