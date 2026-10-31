package com.lockin.game;

import android.view.MotionEvent;

/** UI thread writes; simulation atomically consumes held buttons and press edges. */
public final class InputHandler {
    private final Controls controls = new Controls();
    public static int buttonAt(float x, float y) { return Controls.buttonAt(x, y); }
    public void onTouch(MotionEvent event, float scale, float ox, float oy) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { clear(); return; }
        int held = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if ((event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP)
                    && i == event.getActionIndex()) continue;
            int button = buttonAt((event.getX(i) - ox) / scale, (event.getY(i) - oy) / scale);
            if (button >= 0) held |= 1 << button;
        }
        controls.setHeld(held);
    }
    public Controls.Snapshot consume() { return controls.consume(); }
    public void clear() { controls.clear(); }
}
