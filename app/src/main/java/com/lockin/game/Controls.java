package com.lockin.game;

/** Android-independent multitouch button aggregation and queued press edges. */
public final class Controls {
    public static final int LEFT = 0, RIGHT = 1, JUMP = 2, ATTACK = 3, GUARD = 4, COUNT = 5;
    private static final int[] LEFTS = {20, 140, 700, 820, 570};
    private int held, queued, guardPresses;

    public static int left(int button) { return LEFTS[button]; }
    public static int width(int button) { return button == ATTACK ? 120 : 100; }
    public static int buttonAt(float x, float y) {
        if (y < 425 || y > 525) return -1;
        for (int i = 0; i < COUNT; i++) {
            if (x >= left(i) && x <= left(i) + width(i)) return i;
        }
        return -1;
    }
    /** Full mask of buttons currently covered by any non-lifted touch pointer. */
    public synchronized void setHeld(int buttons) {
        if ((buttons & ~held & (1 << GUARD)) != 0) guardPresses = Math.min(1000000, guardPresses + 1);
        queued |= buttons & ~held;
        held = buttons;
    }
    public synchronized Snapshot consume() {
        Snapshot snapshot = new Snapshot(held, queued, guardPresses);
        queued = guardPresses = 0;
        return snapshot;
    }
    public synchronized void clear() { held = queued = guardPresses = 0; }

    public static final class Snapshot {
        public final int move, guardPresses;
        public final boolean jump, attack, guard, guardPressed;
        private Snapshot(int held, int queued, int guardPresses) {
            this.guardPresses = guardPresses;
            move = ((held >> RIGHT) & 1) - ((held >> LEFT) & 1);
            jump = (queued & (1 << JUMP)) != 0;
            attack = (queued & (1 << ATTACK)) != 0;
            guard = (held & (1 << GUARD)) != 0;
            guardPressed = guard && (queued & (1 << GUARD)) != 0;
        }
    }
}
