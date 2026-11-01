package android.graphics;

/** Tracks dimensions and allocations, not Android raster/GPU behavior. */
public final class Bitmap {
  public static final int DENSITY_NONE = 0;
  public static int created,maxWidth,maxHeight;

  public enum Config {
    ARGB_8888
  }

  private final int width, height;

  public Bitmap(int width, int height) {
    this.width = width;
    this.height = height;
    created++;maxWidth=Math.max(maxWidth,width);maxHeight=Math.max(maxHeight,height);
  }

  public static Bitmap createBitmap(int w, int h, Config config) {
    return new Bitmap(w, h);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void setDensity(int density) {}

  public void recycle() {}
}
