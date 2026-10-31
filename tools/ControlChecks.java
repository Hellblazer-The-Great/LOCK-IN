package com.lockin.game;

public final class ControlChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Controls c = new Controls();
        int guard = 1 << Controls.GUARD, left = 1 << Controls.LEFT;
        c.setHeld(guard | left);
        Controls.Snapshot s = c.consume();
        check(s.guard && s.guardPressed && s.move == -1, "guard down while moving");
        c.setHeld(guard | left | (1 << Controls.JUMP) | (1 << Controls.ATTACK)); s = c.consume();
        check(s.guard && !s.guardPressed && s.jump && s.attack, "hold guard while jump and attack fingers press");
        c.setHeld(guard); s = c.consume();
        check(s.guard && !s.guardPressed && s.move == 0 && !s.jump && !s.attack, "other fingers lifted first");
        c.setHeld(left); s = c.consume();
        check(!s.guard && !s.guardPressed && s.move == -1, "guard lifted first preserves movement");
        c.setHeld(guard); c.consume(); c.setHeld(0); c.setHeld(guard); s = c.consume();
        check(s.guard && s.guardPressed, "release and repress between simulation frames preserved");
        s = c.consume(); check(s.guard && !s.guardPressed, "consume does not repeat guard edge");
        c.setHeld(0); c.setHeld(guard); c.setHeld(0); s = c.consume();
        check(!s.guard && !s.guardPressed, "short released tap cannot leave stale guard");
        c.setHeld(guard | left); c.clear(); s = c.consume();
        check(!s.guard && !s.guardPressed && s.move == 0 && !s.jump, "cancel/pause clear all inputs");
        c.setHeld(left | (1 << Controls.RIGHT)); check(c.consume().move == 0, "opposing movement cancels");
        for (int i = 0; i < Controls.COUNT; i++) {
            check(Controls.buttonAt(Controls.left(i) + Controls.width(i) / 2f, 475) == i,
                    "rendered button matches input bounds " + i);
        }
        check(Controls.buttonAt(550, 475) == -1 && Controls.buttonAt(600, 424) == -1, "gaps do not guard");
        System.out.println("Passed " + checks + " control checks");
    }
}
