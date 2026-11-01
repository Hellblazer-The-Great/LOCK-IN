package com.lockin.game;

import android.view.MotionEvent;

/** UI thread writes; simulation atomically consumes held buttons and press edges. */
public final class InputHandler {
    private final Controls controls = new Controls();
    private boolean modal, ignoreUntilUp;
    private int command;
    public synchronized int consumeCommand() { int result=command;command=0;return result; }
    public synchronized boolean modal() { return modal; }
    public synchronized void resetInput() { controls.clear(); ignoreUntilUp=true; }
    public static boolean resetAt(float x,float y) { return x>=420 && x<=540 && y>=10 && y<=58; }
    public static int modalAt(float x,float y) {
        if(y<255 || y>325) return 0;
        return x>=270 && x<=460?2:x>=500 && x<=690?3:0;
    }
    public static int buttonAt(float x, float y) { return Controls.buttonAt(x, y); }
    public synchronized void onTouch(MotionEvent event, float scale, float ox, float oy) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int i=event.getActionIndex(); float x=(event.getX(i)-ox)/scale,y=(event.getY(i)-oy)/scale;
            int selected=modal?modalAt(x,y):resetAt(x,y)?1:0;
            if(selected!=0) { command=selected; modal=selected==1; controls.clear();ignoreUntilUp=true;return; }
        }
        if(modal || ignoreUntilUp) {
            controls.clear();
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) ignoreUntilUp=false;
            return;
        }
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
    public void consume(Controls.Snapshot target) { controls.consume(target); }
    public synchronized void clear() { controls.clear(); ignoreUntilUp=false; }
}
