package io.github.k8nowplaying;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import java.io.InputStream;

/**
 * Asks the k8np.keys NEMESIS helper (system uid) to replay a steering-wheel media key. The app
 * cannot call the mode manager itself: it drops keys from callers without INJECT_EVENTS.
 * Codes confirmed in the car on 2026-11-16: 166 AVRCP backward, 167 forward, 91 (KEYCODE_MUTE, the steering
 * mute switch) pause/play. 164 is KEYCODE_VOLUME_MUTE and does not pause.
 */
final class MediaKeys {
    static final int PREVIOUS = 166, NEXT = 167, PLAY_PAUSE = 91;
    static final String SOCKET = "k8np.keys";

    interface Result {
        void done(boolean ok);
    }

    private MediaKeys() {}

    static void send(final int keyCode, final Result result) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                LocalSocket s = new LocalSocket();
                try {
                    s.connect(new LocalSocketAddress(SOCKET));
                    s.setSoTimeout(2000);
                    s.getOutputStream().write((keyCode + "\n").getBytes("US-ASCII"));
                    InputStream in = s.getInputStream();
                    byte[] buf = new byte[16];
                    int n = in.read(buf);
                    String reply = n > 0 ? new String(buf, 0, n, "US-ASCII").trim() : "";
                    ok = "ok".equals(reply);
                    Log.i(OverlayService.TAG, "key " + keyCode + " -> " + reply);
                } catch (Exception e) {
                    Log.w(OverlayService.TAG, "key helper unavailable: " + e);
                } finally {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                }
                if (result != null) result.done(ok);
            }
        }, "k8np-key").start();
    }
}
