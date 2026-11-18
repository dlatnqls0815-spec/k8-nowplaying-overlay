package io.github.k8nowplaying.hook;

import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;

/**
 * HMG-Seogaemo hook (verb hook_apps) inside the right split-screen app. When a Bluetooth call goes
 * off hook, SplitScreenHome.i(state=2, callType=0) queues message 0x3f1 on its handler "pv" (50 ms
 * delay) to turn the split screen to the phone page; the now-playing overlay shows the call instead.
 * This removes only that queued message right after the method returns, so the stock call state,
 * the phone view and the restore on hang-up stay as they are. Found in the car's RightScreenHome
 * (260902 build) on 2026-11-17; the hook checks the names and does nothing if they differ.
 */
public class CallSplitEntry extends ModEntry {
    static final String HOST = "com.mobis.splitscreen.right";
    static final String HOME = "com.mobis.splitscreen.right.SplitScreenHome";
    static final int STATE_OFFHOOK = 2, CALL_TYPE_BT = 0, MSG_SHOW_PAGE = 0x3f1;
    private static boolean hooked;

    @Override
    public void appLoaded(final ModContext ctx, LoadedApp app) {
        try {
            if (!HOST.equals(app.processName) && !HOST.equals(app.packageName)) return;
            synchronized (CallSplitEntry.class) {
                if (hooked) return;
                hooked = true;
            }
            Class<?> home = Hooks.findClass(HOME, app.classLoader);
            Hooks.findAndHookMethod(home, "i", int.class, int.class, new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    try {
                        if (!shouldBlock(p.args)) return;
                        Object handler = Hooks.getObjectField(p.thisObject, "pv");
                        if (handler == null) return;
                        Hooks.callMethod(handler, "removeMessages", MSG_SHOW_PAGE);
                        ctx.log("k8np: kept split screen page on call (removed 0x3f1)");
                    } catch (Throwable t) {
                        ctx.error("k8np: call split hook failed", t);
                    }
                }
            });
            ctx.log("k8np: call split hook ready in " + app.processName);
        } catch (Throwable t) {
            try { ctx.error("k8np: call split hook not installed", t); } catch (Throwable ignored) { }
        }
    }

    /** Only a Bluetooth phone call going off hook; Bluelink, emergency and idle pass untouched. */
    static boolean shouldBlock(Object[] args) {
        return args != null && args.length == 2
                && Integer.valueOf(STATE_OFFHOOK).equals(args[0])
                && Integer.valueOf(CALL_TYPE_BT).equals(args[1]);
    }
}
