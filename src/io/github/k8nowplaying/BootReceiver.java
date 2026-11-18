package io.github.k8nowplaying;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Prefs.enabled(Prefs.of(context))) {
            context.startService(new Intent(context, OverlayService.class));
        }
    }
}
