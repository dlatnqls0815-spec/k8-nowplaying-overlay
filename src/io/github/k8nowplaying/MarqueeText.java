package io.github.k8nowplaying;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * One line of text that scrolls sideways when it does not fit, so long titles and artist names
 * can be read in full. A TextView marquee needs focus or selection, which a not-focusable overlay
 * window does not reliably give on Android 4.4, so this view draws and scrolls the text itself.
 * The text waits at the start, scrolls left until a repeated copy arrives, and starts over.
 */
final class MarqueeText extends View {
    static final long PAUSE_MS = 1800;
    static final float SPEED_PX_PER_S = 55f;
    static final long FRAME_MS = 33;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "";
    private float textWidth;
    private long startMs;

    MarqueeText(Context c) {
        super(c);
    }

    /** Restarts scrolling only when the text really changes (the card refreshes twice a second). */
    void setText(String s) {
        if (s == null) s = "";
        if (s.equals(text)) return;
        text = s;
        textWidth = paint.measureText(text);
        startMs = SystemClock.uptimeMillis();
        invalidate();
    }

    void setTextSizePx(float px) {
        if (paint.getTextSize() == px) return;
        paint.setTextSize(px);
        textWidth = paint.measureText(text);
        requestLayout();
        invalidate();
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    void setBold(boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textWidth = paint.measureText(text);
    }

    /** Gap between the end of the text and its repeated copy. */
    float gap() {
        return paint.getTextSize() * 3;
    }

    /**
     * How far the text is scrolled left after elapsedMs: 0 during the pause, then moving at
     * SPEED_PX_PER_S until it has moved one text width plus the gap, then the cycle repeats.
     */
    static float offset(long elapsedMs, float textWidth, float gap, float viewWidth) {
        if (textWidth <= viewWidth || elapsedMs <= 0) return 0;
        float distance = textWidth + gap;
        long scrollMs = (long) (distance / SPEED_PX_PER_S * 1000);
        long cycle = PAUSE_MS + scrollMs;
        long t = elapsedMs % cycle;
        if (t < PAUSE_MS) return 0;
        return (t - PAUSE_MS) * SPEED_PX_PER_S / 1000f;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        int h = fm.descent - fm.ascent + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        if (w <= 0 || text.isEmpty()) return;
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        float baseline = getPaddingTop() - fm.ascent;
        float left = getPaddingLeft();
        canvas.save();
        canvas.clipRect(left, 0, left + w, getHeight());
        if (textWidth <= w) {
            canvas.drawText(text, left, baseline, paint);
        } else {
            float off = offset(SystemClock.uptimeMillis() - startMs, textWidth, gap(), w);
            canvas.drawText(text, left - off, baseline, paint);
            canvas.drawText(text, left - off + textWidth + gap(), baseline, paint);
            postInvalidateDelayed(FRAME_MS);
        }
        canvas.restore();
    }
}
