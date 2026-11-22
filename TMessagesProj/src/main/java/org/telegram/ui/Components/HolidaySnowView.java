package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.Random;

/**
 * Optional, purely visual snow overlay for the dialogs screen.
 * Does not alter Telegram message or privacy behavior.
 */
public final class HolidaySnowView extends View {
    private static final int COUNT = 24;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] radius = new float[COUNT];
    private final float[] speed = new float[COUNT];
    private final Random random = new Random();
    private long lastFrame;

    public HolidaySnowView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setColor(Color.WHITE);
        paint.setAlpha(175);
        for (int i = 0; i < COUNT; i++) {
            x[i] = random.nextFloat();
            y[i] = random.nextFloat();
            radius[i] = 1.5f + random.nextFloat() * 2f;
            speed[i] = 0.025f + random.nextFloat() * 0.035f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (int i = 0; i < COUNT; i++) {
            y[i] += speed[i] * dt;
            if (y[i] > 1f) {
                y[i] = 0f;
                x[i] = random.nextFloat();
            }
            canvas.drawCircle(x[i] * getWidth(), y[i] * getHeight(),
                    radius[i] * getResources().getDisplayMetrics().density, paint);
        }
        if (isAttachedToWindow() && getVisibility() == VISIBLE) {
            postInvalidateDelayed(33L);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        lastFrame = 0L;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            lastFrame = 0L;
            invalidate();
        }
    }
}
