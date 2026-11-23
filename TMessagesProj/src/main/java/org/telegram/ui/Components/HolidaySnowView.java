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
    private long nextVortexAt;
    private long vortexStart;
    private int direction = 1;
    private float vortexY;
    private final Paint windPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private void scheduleVortex(long now) {
        nextVortexAt = now + 60000L + random.nextInt(120000);
        vortexStart = 0L;
    }

    public HolidaySnowView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setColor(Color.WHITE);
        paint.setAlpha(135);
        windPaint.setStyle(Paint.Style.STROKE);
        windPaint.setStrokeCap(Paint.Cap.ROUND);
        scheduleVortex(android.os.SystemClock.uptimeMillis());
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
        if (vortexStart == 0L && now >= nextVortexAt) {
            vortexStart = now;
            direction = random.nextBoolean() ? 1 : -1;
            vortexY = 0.2f + random.nextFloat() * 0.55f;
        }
        float phase = vortexStart == 0L ? -1f : (now - vortexStart) / 3000f;
        boolean active = phase >= 0f && phase < 1f;
        if (phase >= 1f) scheduleVortex(now);
        float vx = direction == 1 ? -0.15f + phase * 1.3f : 1.15f - phase * 1.3f;
        for (int i = 0; i < COUNT; i++) {
            y[i] += speed[i] * dt;
            x[i] += (float)Math.sin(now * 0.0007 + i * 2.3) * dt * 0.009f;
            if (x[i] < 0f) x[i] += 1f;
            if (x[i] > 1f) x[i] -= 1f;
            if (active) {
                float dx = x[i] - vx;
                float dy = (y[i] - vortexY) * getHeight() / Math.max(1f, getWidth());
                float dist = dx * dx + dy * dy;
                if (dist < 0.025f) {
                    float force = (0.025f - dist) / 0.025f * dt;
                    x[i] += (-dy * 2f + direction * 0.3f) * force;
                    y[i] += dx * force * 1.2f;
                }
            }
            if (y[i] > 1f) {
                y[i] = 0f;
                x[i] = random.nextFloat();
            }
            canvas.drawCircle(x[i] * getWidth(), y[i] * getHeight(),
                    radius[i] * getResources().getDisplayMetrics().density, paint);
        }
        if (active) {
            float density = getResources().getDisplayMetrics().density;
            float cx = vx * getWidth(), cy = vortexY * getHeight();
            windPaint.setStrokeWidth(1.2f * density);
            windPaint.setColor(Color.argb((int)(85 * Math.sin(Math.PI * phase)), 215, 238, 255));
            for (int j = 0; j < 3; j++) {
                float rx = (13 + j * 7) * density, ry = (7 + j * 4) * density;
                canvas.drawArc(cx - rx, cy - ry, cx + rx, cy + ry,
                        j * 80 + phase * 400, 195, false, windPaint);
            }
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
