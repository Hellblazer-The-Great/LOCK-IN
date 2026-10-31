package android.view;

/** JVM-only test double for the MotionEvent methods consumed by InputHandler.
 * Not packaged in the app; device/framework touch delivery still needs manual testing. */
public final class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3,
            ACTION_POINTER_DOWN = 5, ACTION_POINTER_UP = 6;
    private final int action, index;
    private final float[] xs, ys;
    public MotionEvent(int action, int index, float[] xs, float[] ys) {
        this.action = action; this.index = index; this.xs = xs; this.ys = ys;
    }
    public int getActionMasked() { return action; }
    public int getActionIndex() { return index; }
    public int getPointerCount() { return xs.length; }
    public float getX(int index) { return xs[index]; }
    public float getY(int index) { return ys[index]; }
}
