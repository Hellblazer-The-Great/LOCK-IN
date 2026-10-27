package com.lockin.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private GameScreen screen;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen = new GameScreen(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(screen);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); screen.resume(); }
    @Override protected void onPause() { screen.pause(); super.onPause(); }
}
