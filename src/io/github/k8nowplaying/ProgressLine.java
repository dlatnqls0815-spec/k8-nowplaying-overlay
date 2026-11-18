package io.github.k8nowplaying;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Thin playback progress line along the bottom of the card. */
final class ProgressLine extends View {
    private final Paint track = new Paint(), fill = new Paint();
    private float fraction;

    ProgressLine(Context context) {
        super(context);
        track.setColor(0x33FFFFFF);
        fill.setColor(0xFF2F9BFF);
    }

    void set(long position, long duration) {
        float f = duration > 0 ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
        if (Math.abs(f - fraction) > 0.0005f) {
            fraction = f;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, track);
        canvas.drawRect(0, 0, w * fraction, h, fill);
    }
}
