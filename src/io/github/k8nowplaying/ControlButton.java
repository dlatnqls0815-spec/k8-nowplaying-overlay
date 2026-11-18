package io.github.k8nowplaying;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/** Media control drawn with paths, since the 4.4 system font lacks the media glyphs. */
final class ControlButton extends View {
    static final int PREVIOUS = 0, PLAY = 1, PAUSE = 2, NEXT = 3;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int shape;
    private boolean down;

    ControlButton(Context context, int shape) {
        super(context);
        this.shape = shape;
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        pressedBg.setColor(0x33FFFFFF);
        setClickable(true);
    }

    void setShape(int s) {
        if (s != shape) {
            shape = s;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        boolean was = down;
        down = a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE;
        if (was != down) invalidate();
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        if (down) c.drawCircle(cx, cy, r * 0.9f, pressedBg);
        float s = r * (shape == PLAY || shape == PAUSE ? 0.5f : 0.42f);
        path.reset();
        switch (shape) {
            case PLAY:
                triangle(cx - s * 0.7f, cy, s * 1.6f, true);
                break;
            case PAUSE:
                c.drawRect(cx - s * 0.75f, cy - s, cx - s * 0.2f, cy + s, paint);
                c.drawRect(cx + s * 0.2f, cy - s, cx + s * 0.75f, cy + s, paint);
                break;
            case NEXT:
                triangle(cx - s, cy, s * 1.5f, true);
                c.drawRect(cx + s * 0.55f, cy - s, cx + s * 0.85f, cy + s, paint);
                break;
            default:
                triangle(cx + s, cy, s * 1.5f, false);
                c.drawRect(cx - s * 0.85f, cy - s, cx - s * 0.55f, cy + s, paint);
                break;
        }
        c.drawPath(path, paint);
    }

    /** Triangle of the given width starting at x, pointing right (or left when !right). */
    private void triangle(float x, float cy, float width, boolean right) {
        float half = width * 0.62f;
        float tip = right ? x + width : x - width;
        path.moveTo(x, cy - half);
        path.lineTo(tip, cy);
        path.lineTo(x, cy + half);
        path.close();
    }
}
