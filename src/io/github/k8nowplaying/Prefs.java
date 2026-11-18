package io.github.k8nowplaying;

import android.content.Context;
import android.content.SharedPreferences;

/** Bar placement and options. Screen is 1920x720 on the K8 Gen5W. */
final class Prefs {
    static final String ENABLED = "enabled", X = "x", Y = "y", WIDTH = "width", HEIGHT = "height",
            TEXT = "text", ALPHA = "alpha", NAVI_ONLY = "naviOnly",
            HIDE_STOCK_POPUP = "hideStockPopup", SHOW_CALLS = "showCalls";

    private Prefs() {}

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("overlay2", Context.MODE_PRIVATE);
    }

    static int x(SharedPreferences p) { return p.getInt(X, 1130); }
    static int y(SharedPreferences p) { return p.getInt(Y, 636); }
    static int width(SharedPreferences p) { return p.getInt(WIDTH, 700); }
    static int height(SharedPreferences p) { return p.getInt(HEIGHT, 78); }
    static int text(SharedPreferences p) { return p.getInt(TEXT, 24); }
    static int alpha(SharedPreferences p) { return p.getInt(ALPHA, 92); }
    static boolean enabled(SharedPreferences p) { return p.getBoolean(ENABLED, true); }
    static boolean naviOnly(SharedPreferences p) { return p.getBoolean(NAVI_ONLY, true); }
    static boolean hideStockPopup(SharedPreferences p) { return p.getBoolean(HIDE_STOCK_POPUP, true); }
    static boolean showCalls(SharedPreferences p) { return p.getBoolean(SHOW_CALLS, true); }
}
