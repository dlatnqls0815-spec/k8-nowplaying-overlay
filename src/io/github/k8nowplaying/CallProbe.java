package io.github.k8nowplaying;

import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import dalvik.system.PathClassLoader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Reads the Bluetooth hands-free call state for the call card. The K8 framework ships the
 * Broadcom hands-free API (com.broadcom.bt.hfdevice.BluetoothHfDevice); in the car on 2026-11-17
 * a normal app could open it and read getCallStateInfo (setup 2 dialing, 3 alerting, 4 incoming,
 * 6 idle; active/held counts). The phone number came back empty there, so the number and the
 * stock caller name come from the event handler's onMobisBTCallInfo. The Bluetooth service keeps
 * event handlers in a RemoteCallbackList (HfDeviceService.registerEventHandler), so registering
 * ours does not displace the stock phone app's. answer()/hangup() run only from the card buttons.
 */
final class CallProbe {
    static final String HF_CLASS = "com.broadcom.bt.hfdevice.BluetoothHfDevice";
    static final String HF_JAR = "/system/framework/com.broadcom.bt.jar";
    static final int HF_PROFILE = 100;
    static final long IDLE_POLL_MS = 3000, CALL_POLL_MS = 1000;

    /** One reading of the call state. */
    static final class State {
        static final State NONE = new State(6, 0, 0, "");
        final int setup, active, held;
        final String number;

        State(int setup, int active, int held, String number) {
            this.setup = setup;
            this.active = active;
            this.held = held;
            this.number = number == null ? "" : number;
        }

        boolean any() {
            return active > 0 || held > 0 || (setup >= 2 && setup <= 5);
        }
    }

    interface Listener {
        void onCallState(State state);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler();
    private Object proxy;
    private Class<?> hfClass;
    private volatile String infoNumber = "", infoName = "";
    private String last = "";
    private boolean stopped, inCall;

    CallProbe(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        try {
            Class<?> hf = load();
            hfClass = hf;
            Object ok = hf.getMethod("getProxy", Context.class, BluetoothProfile.ServiceListener.class)
                    .invoke(null, context, serviceListener);
            Log.i(OverlayService.TAG, "call probe getProxy=" + ok);
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "call probe unavailable: " + t);
        }
    }

    /** A call broadcast arrived: read now instead of waiting for the next poll. */
    void readSoon() {
        if (stopped || proxy == null) return;
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 150);
    }

    void stop() {
        stopped = true;
        handler.removeCallbacks(poll);
        if (proxy != null) {
            try {
                proxy.getClass().getMethod("unregisterEventHandler").invoke(proxy);
            } catch (Throwable ignored) {
            }
            try {
                proxy.getClass().getMethod("closeProxy").invoke(proxy);
            } catch (Throwable ignored) {
            }
            proxy = null;
        }
    }

    private Class<?> load() throws ClassNotFoundException {
        try {
            return Class.forName(HF_CLASS);
        } catch (ClassNotFoundException e) {
            return new PathClassLoader(HF_JAR, context.getClassLoader()).loadClass(HF_CLASS);
        }
    }

    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile p) {
            Log.i(OverlayService.TAG, "call probe connected profile=" + profile + " " + p.getClass().getName());
            if (profile != HF_PROFILE || stopped) return;
            proxy = p;
            registerEvents();
            handler.removeCallbacks(poll);
            handler.post(poll);
        }

        @Override
        public void onServiceDisconnected(int profile) {
            Log.i(OverlayService.TAG, "call probe disconnected profile=" + profile);
            proxy = null;
            handler.removeCallbacks(poll);
            inCall = false;
            listener.onCallState(State.NONE);
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (stopped || proxy == null) return;
            State s = read();
            inCall = s.any();
            String now = "setup=" + s.setup + " active=" + s.active + " held=" + s.held
                    + " number=" + (s.number.isEmpty() ? "none" : "yes");
            if (!now.equals(last)) {
                last = now;
                Log.i(OverlayService.TAG, "call probe " + now);
            }
            listener.onCallState(s);
            handler.postDelayed(this, inCall ? CALL_POLL_MS : IDLE_POLL_MS);
        }
    };

    private State read() {
        try {
            Object devices = proxy.getClass().getMethod("getConnectedDevices").invoke(proxy);
            if (!(devices instanceof List) || ((List<?>) devices).isEmpty()) return State.NONE;
            Object device = ((List<?>) devices).get(0);
            Method info = null;
            for (Method m : proxy.getClass().getMethods()) {
                if (m.getName().equals("getCallStateInfo") && m.getParameterTypes().length == 1) info = m;
            }
            Object s = info == null ? null : info.invoke(proxy, device);
            if (s == null) return State.NONE;
            String number = string(s, "getPhoneNumber");
            State st = new State(integer(s, "getCallSetupState", 6), integer(s, "getNumActiveCall", 0),
                    integer(s, "getNumHeldCall", 0), number.isEmpty() ? infoNumber : number);
            if (!st.any()) {
                infoNumber = "";
                infoName = "";
            }
            return st;
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "call probe read failed: " + t);
            return State.NONE;
        }
    }

    /** Stock caller name from the latest MobisBTCallInfo (empty when unknown or hidden). */
    String callerName() {
        return infoName;
    }

    /** Card button: answer the ringing call. */
    boolean answer() {
        return command("answer");
    }

    /** Card button: reject a ringing call or end the current one. */
    boolean hangup() {
        return command("hangup");
    }

    private boolean command(String name) {
        try {
            Object ok = proxy == null ? null : proxy.getClass().getMethod(name).invoke(proxy);
            Log.i(OverlayService.TAG, "call " + name + " -> " + ok);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "call " + name + " failed: " + t);
            return false;
        }
    }

    /** Registers a dynamic IBluetoothHfDeviceEventHandler that only reads call info. */
    private void registerEvents() {
        try {
            ClassLoader loader = hfClass.getClassLoader();
            Class<?> iface = loader.loadClass("com.broadcom.bt.hfdevice.IBluetoothHfDeviceEventHandler");
            Object events = Proxy.newProxyInstance(loader, new Class<?>[] {iface}, new InvocationHandler() {
                @Override
                public Object invoke(Object self, Method m, Object[] args) {
                    String n = m.getName();
                    if (n.equals("equals")) return args != null && args.length == 1 && self == args[0];
                    if (n.equals("hashCode")) return System.identityHashCode(self);
                    if (n.equals("toString")) return "K8NowPlayingCallEvents";
                    if (n.equals("onMobisBTCallInfo") && args != null && args.length == 1) {
                        takeInfo(args[0]);
                    } else if (n.equals("onCallStateChange") && args != null && args.length == 6
                            && args[4] instanceof String && !((String) args[4]).isEmpty()) {
                        infoNumber = (String) args[4];
                        postRead();
                    }
                    return null;
                }
            });
            proxy.getClass().getMethod("registerEventHandler", iface).invoke(proxy, events);
            proxy.getClass().getMethod("getMobisBTCallInfo").invoke(proxy);
            Log.i(OverlayService.TAG, "call events registered");
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "call events unavailable: " + t);
        }
    }

    private void postRead() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                readSoon();
            }
        });
    }

    /** Picks the representative call's number and stock caller name from a MobisBTCallInfo. */
    private void takeInfo(Object info) {
        try {
            int calls = integer(info, "GetNumberOfCalls", 0);
            int want = integer(info, "GetRepresentCallStatus", -1);
            String number = "", name = "";
            for (int i = 0; i < Math.max(calls, 2); i++) {
                String num = string(info, "GetCallerNumber", i);
                if (num.isEmpty()) continue;
                if (number.isEmpty() || integer(info, "GetCallStatus", i, -2) == want) {
                    number = num;
                    name = string(info, "GetCallerName", i);
                }
            }
            if (number.isEmpty()) number = string(info, "GetIncomingNumber");
            infoNumber = number;
            infoName = name;
            Log.i(OverlayService.TAG, "call info calls=" + calls + " status=" + want
                    + " number=" + (number.isEmpty() ? "none" : "yes") + " name=" + (name.isEmpty() ? "none" : "yes"));
            postRead();
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "call info failed: " + t);
        }
    }

    private static String string(Object target, String name, int index) {
        try {
            Object v = target.getClass().getMethod(name, int.class).invoke(target, index);
            return v instanceof String ? ((String) v).trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static int integer(Object target, String name, int index, int fallback) {
        try {
            Object v = target.getClass().getMethod(name, int.class).invoke(target, index);
            return v instanceof Number ? ((Number) v).intValue() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int integer(Object target, String name, int fallback) {
        Object v = call(target, name);
        return v instanceof Number ? ((Number) v).intValue() : fallback;
    }

    private static String string(Object target, String name) {
        Object v = call(target, name);
        return v instanceof String ? (String) v : "";
    }

    private static Object call(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
