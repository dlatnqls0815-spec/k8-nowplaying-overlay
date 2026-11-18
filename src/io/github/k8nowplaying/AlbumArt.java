package io.github.k8nowplaying;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import java.io.File;

/**
 * Picks up the Bluetooth (AVRCP cover art) image the stock audio app requests on each track.
 * The Bluetooth stack writes /mnt/bt_cover_art/<n>/<handle>.jpg as 0600, makes it world-readable
 * (0644) once the transfer completes, and deletes it about 5 s later (seen in the car on
 * 2026-11-16), so the folder is polled and the newest readable file is decoded and kept.
 */
final class AlbumArt {
    static final File[] DIRS = {new File("/mnt/bt_cover_art/0"), new File("/mnt/bt_cover_art/1")};
    static final long KEEP_ON_TRACK_CHANGE_MS = 3000;

    private String lastKey = "";
    private Bitmap bitmap;
    private long loadedAtMs;

    Bitmap bitmap() {
        return bitmap;
    }

    /** The track changed: drop the old cover unless one just arrived for the new track. */
    void trackChanged() {
        if (SystemClock.elapsedRealtime() - loadedAtMs > KEEP_ON_TRACK_CHANGE_MS) bitmap = null;
    }

    /** Returns true when a new cover was loaded. */
    boolean scan(int targetPx) {
        File newest = null;
        for (File dir : DIRS) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.getName().endsWith(".jpg") || f.length() <= 0 || !f.canRead()) continue;
                if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
            }
        }
        if (newest == null) return false;
        String key = newest.getPath() + ":" + newest.length() + ":" + newest.lastModified();
        if (key.equals(lastKey)) return false;
        Bitmap b = decode(newest.getPath(), targetPx);
        if (b == null) return false; // still being written; try again on the next poll
        lastKey = key;
        bitmap = b;
        loadedAtMs = SystemClock.elapsedRealtime();
        return true;
    }

    static Bitmap decode(String path, int targetPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(Math.min(o.outWidth, o.outHeight), targetPx);
        return BitmapFactory.decodeFile(path, o);
    }

    /** Largest power of two that keeps the short side at or above the target size. */
    static int sampleSize(int shortSide, int targetPx) {
        int s = 1;
        while (targetPx > 0 && shortSide / (s * 2) >= targetPx) s *= 2;
        return s;
    }
}
