package com.lockin.game;

import android.view.MotionEvent;

/** Exercises the actual Android adapter against a minimal MotionEvent test double. */
public final class InputChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void touch(InputHandler input, int action, int index, float... xs) {
        float[] ys = new float[xs.length];
        for (int i = 0; i < ys.length; i++) ys[i] = 475;
        input.onTouch(new MotionEvent(action, index, xs, ys), 1, 0, 0);
    }
    public static void main(String[] args) {
        InputHandler input = new InputHandler();
        touch(input, MotionEvent.ACTION_DOWN, 0, 620);
        Controls.Snapshot s = input.consume();
        check(s.guard && s.guardPressed, "guard touch down immediately held");
        touch(input, MotionEvent.ACTION_POINTER_DOWN, 1, 620, 70);
        s = input.consume(); check(s.guard && !s.guardPressed && s.move == -1, "second movement pointer preserves guard age");
        touch(input, MotionEvent.ACTION_POINTER_UP, 0, 620, 70);
        s = input.consume(); check(!s.guard && s.move == -1, "lifting guard pointer excludes it from held set");
        touch(input, MotionEvent.ACTION_UP, 0, 70);
        check(input.consume().move == 0, "last movement pointer releases");
        touch(input, MotionEvent.ACTION_DOWN, 0, 620); input.consume();
        touch(input, MotionEvent.ACTION_POINTER_DOWN, 1, 620, 630); input.consume();
        touch(input, MotionEvent.ACTION_POINTER_UP, 0, 620, 630);
        s = input.consume(); check(s.guard && !s.guardPressed, "one of two guard fingers lifts without refreshing");
        touch(input, MotionEvent.ACTION_UP, 0, 630);
        check(!input.consume().guard, "last guard finger releases");
        touch(input, MotionEvent.ACTION_DOWN, 0, 70); input.consume();
        touch(input, MotionEvent.ACTION_MOVE, 0, 620);
        s = input.consume(); check(s.guard && s.guardPressed && s.move == 0, "drag into guard raises it");
        touch(input, MotionEvent.ACTION_MOVE, 0, 750);
        s = input.consume(); check(!s.guard && s.jump, "drag off guard lowers it and preserves jump edge");
        touch(input, MotionEvent.ACTION_CANCEL, 0, 620, 70);
        s = input.consume(); check(!s.guard && !s.jump && s.move == 0, "touch cancellation clears held and queued state");
        input.onTouch(new MotionEvent(MotionEvent.ACTION_DOWN, 0,
                new float[]{1245}, new float[]{957}), 2, 5, 7);
        s = input.consume(); check(s.guard && s.guardPressed, "letterbox and scale transform matches guard button");
        input.clear(); check(!input.consume().guard, "lifecycle clear releases guard");
        System.out.println("Passed " + checks + " touch adapter checks");
    }
}
