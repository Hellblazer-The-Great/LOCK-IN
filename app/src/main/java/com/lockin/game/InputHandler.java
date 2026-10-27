package com.lockin.game;

import android.view.MotionEvent;

/** UI thread writes; simulation atomically consumes a snapshot, including action edges. */
public final class InputHandler {
    private boolean left, right, jumpHeld, attackHeld, jumpQueued, attackQueued;
    public static int buttonAt(float x, float y) {
        if (y < 425 || y > 525) return -1;
        if (x >= 20 && x <= 120) return 0;
        if (x >= 140 && x <= 240) return 1;
        if (x >= 700 && x <= 800) return 2;
        if (x >= 820 && x <= 940) return 3;
        return -1;
    }
    public synchronized void onTouch(MotionEvent event, float scale, float ox, float oy) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { clear(); return; }
        boolean[] held = new boolean[4];
        for (int i = 0; i < event.getPointerCount(); i++) {
            if ((event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP)
                    && i == event.getActionIndex()) continue;
            int button = buttonAt((event.getX(i) - ox) / scale, (event.getY(i) - oy) / scale);
            if (button >= 0) held[button] = true;
        }
        left = held[0]; right = held[1];
        jumpQueued |= held[2] && !jumpHeld;
        attackQueued |= held[3] && !attackHeld;
        jumpHeld = held[2]; attackHeld = held[3];
    }
    public synchronized int[] consume() {
        int[] result = { (right ? 1 : 0) - (left ? 1 : 0), jumpQueued ? 1 : 0, attackQueued ? 1 : 0 };
        jumpQueued = attackQueued = false;
        return result;
    }
    public synchronized void clear() {
        left = right = jumpHeld = attackHeld = jumpQueued = attackQueued = false;
    }
}
