package io.github.k8nowplaying;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

/**
 * Reads whether the stock phone is in privacy mode (number and name hidden), the way the stock
 * Bluetooth phone app asks the Bluetooth contacts provider. In the car the provider answers 1
 * while the profile setting is on and the passenger seat is occupied ("Privacy On virtual").
 * Read only: selection args {user id, "1", "read"}; the provider's "write" branch is never used.
 */
final class PrivacyMode {
    static final Uri URI = Uri.parse("content://com.android.contacts_bt/profile_privacy_mode");
    static final String USER_SETTING = "user_profile_driver";

    private PrivacyMode() {}

    static boolean read(Context c) {
        String user = String.valueOf(Settings.System.getInt(c.getContentResolver(), USER_SETTING, 1));
        Cursor cursor = null;
        try {
            cursor = c.getContentResolver().query(URI, null, null, new String[] {user, "1", "read"}, null);
            boolean on = cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 1;
            Log.i(OverlayService.TAG, "privacy mode user=" + user + " on=" + on);
            return on;
        } catch (RuntimeException e) {
            Log.w(OverlayService.TAG, "privacy mode read failed: " + e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
