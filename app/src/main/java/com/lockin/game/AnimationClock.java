package com.lockin.game;

/** Pure Java playback: looping/one-shot ranges and authoritative combat-phase sampling. */
public final class AnimationClock {
    private SpriteClip clip;
    private int first, last, key;
    private double fps, elapsed;
    private boolean loop;
    private int frame;
    public SpriteClip clip() { return clip; }
    public int frame() { return frame; }
    public void reset() { clip = null; elapsed = 0; frame = 0; }
    public void play(SpriteClip next, int start, int end, double speed, boolean looping, int stateKey) {
        if (start < 0 || end < start || end >= next.frames || !Double.isFinite(speed) || speed <= 0)
            throw new IllegalArgumentException("Invalid animation range/speed");
        if (clip != next || key != stateKey || first != start || last != end || fps != speed || loop != looping) {
            clip = next; first = start; last = end; fps = speed; loop = looping;
            key = stateKey; elapsed = 0; frame = first;
        }
    }
    public void advance(double dt) {
        if (clip == null || !Double.isFinite(dt) || dt <= 0) return;
        elapsed += dt;
        int count = last - first + 1;
        if (loop) elapsed %= count / fps;
        else elapsed = Math.min(elapsed, count / fps);
        int offset = (int) Math.floor(elapsed * fps + 1e-8);
        frame = first + (loop ? offset % count : Math.min(count - 1, offset));
    }
    public void sample(double progress) {
        if (clip == null) return;
        double bounded = Double.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        frame = first + Math.min(last - first, (int) (bounded * (last - first + 1)));
    }
}
