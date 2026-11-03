package com.lockin.game;

import android.view.MotionEvent;

/** UI thread writes; simulation atomically consumes held buttons and press edges. */
public final class InputHandler {
    private final Controls controls = new Controls();
    private boolean modal, ignoreUntilUp;
    private boolean inventory;
    private final java.util.ArrayDeque<Integer> commands=new java.util.ArrayDeque<>();
    public synchronized int consumeCommand() { return commands.isEmpty()?0:commands.removeFirst(); }
    public synchronized boolean modal() { return modal; }
    public synchronized void resetInput() { controls.clear(); ignoreUntilUp=true; }
    public static boolean resetAt(float x,float y) { return x>=420 && x<=540 && y>=10 && y<=58; }
    public static int modalAt(float x,float y) {
        if(y<255 || y>325) return 0;
        return x>=270 && x<=460?2:x>=500 && x<=690?3:0;
    }
    /** Logical 960x540 coordinates, matching the renderer's >=48px controls. */
    public static int inventoryAt(float x,float y) {
        if(x>=800&&x<=940&&y>=10&&y<=58) return 4;
        if(x>=20&&x<=380&&y>=80&&y<380) return 100+(int)((y-80)/60);
        if(y>=450&&y<=520) {
            if(x>=20&&x<=120) return 5;
            if(x>=130&&x<=230) return 6;
            if(x>=250&&x<=390) return 10;
            if(x>=410&&x<=560) return 7;
            if(x>=580&&x<=730) return 8;
            if(x>=750&&x<=940) return 9;
        }
        return 0;
    }
    public static int buttonAt(float x, float y) { return Controls.buttonAt(x, y); }
    public synchronized void onTouch(MotionEvent event, float scale, float ox, float oy) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int i=event.getActionIndex(); float x=(event.getX(i)-ox)/scale,y=(event.getY(i)-oy)/scale;
            if(commands.size()>=32) return;
            if(inventory) {
                if(action==MotionEvent.ACTION_DOWN) {
                    int selected=inventoryAt(x,y);
                    if(selected!=0) commands.addLast(selected);
                    if(selected==4) inventory=false;
                }
                controls.clear();ignoreUntilUp=true;return;
            }
            int selected=modal?modalAt(x,y):resetAt(x,y)?1:
                x>=550&&x<=670&&y>=10&&y<=58?4:
                x>=680&&x<=790&&y>=10&&y<=58?11:0;
            if(selected!=0) {commands.addLast(selected); modal=selected==1;
                if(selected==4) inventory=true;
                controls.clear();ignoreUntilUp=true;return; }
        }
        if(inventory || modal || ignoreUntilUp) {
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
