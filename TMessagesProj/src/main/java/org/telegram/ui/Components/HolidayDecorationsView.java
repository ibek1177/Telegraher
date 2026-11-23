package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Optional holiday decorations. No Telegram messaging or privacy APIs are touched.
 * Sounds are disabled by default and only play following a direct tap.
 */
public final class HolidayDecorationsView extends View {
    private static final String PREFS = "holiday_2027";
    private static final String SOUND = "sound_enabled";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final int[] colors = {0xffe75b68, 0xffffd166, 0xff6bd5aa, 0xff73baf5};
    private final int[] bulbs = new int[7];
    private final SharedPreferences preferences;
    private int surprise = -1;
    private long surpriseUntil;
    private float wobble;

    public HolidayDecorationsView(Context context) {
        super(context);
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < bulbs.length; i++) bulbs[i] = i % colors.length;
    }

    public boolean isSoundEnabled() {
        return preferences.getBoolean(SOUND, false);
    }

    public void setSoundEnabled(boolean enabled) {
        preferences.edit().putBoolean(SOUND, enabled).apply();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float spacing = getWidth() / (bulbs.length + 1f);
        float baseY = 19f * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(0xff77987b);
        android.graphics.Path wire = new android.graphics.Path();
        wire.moveTo(0, baseY);
        wire.quadTo(getWidth() / 2f, baseY + 20 * density, getWidth(), baseY);
        canvas.drawPath(wire, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < bulbs.length; i++) {
            float cx = (i + 1) * spacing;
            float cy = baseY + 12f * density * (1f - Math.abs(2f * cx / getWidth() - 1f));
            int color = colors[bulbs[i]];
            float pulse = 0.65f + 0.35f * (float)Math.sin(SystemClock.uptimeMillis() * 0.002 + i * 1.7);
            paint.setColor((color & 0x00ffffff) | (((int)(40 + pulse * 35)) << 24));
            canvas.drawCircle(cx, cy, (10f + pulse * 3f) * density, paint);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, 5.5f * density, paint);
        }
        if (surprise >= 0 && SystemClock.uptimeMillis() < surpriseUntil) {
            paint.setColor(colors[surprise % colors.length]);
            float cx = (surprise + 1) * spacing;
            float cy = baseY + 35f * density;
            float r = (8f + (float)Math.sin(wobble) * 3f) * density;
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(12f * density);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("✦", cx, cy + 4f * density, paint);
            wobble += 0.3f;
            postInvalidateDelayed(50);
        } else {
            surprise = -1;
        }
        if (isAttachedToWindow() && getVisibility() == VISIBLE) postInvalidateDelayed(80L);
    }

    private int touchedBulb(float x, float y) {
        if (getWidth() <= 0) return -1;
        float density = getResources().getDisplayMetrics().density;
        float spacing = getWidth() / (bulbs.length + 1f);
        float baseY = 19f * density;
        for (int i = 0; i < bulbs.length; i++) {
            float cx = (i + 1) * spacing;
            float cy = baseY + 12f * density * (1f - Math.abs(2f * cx / getWidth() - 1f));
            float dx = x - cx;
            float dy = y - cy;
            float hitRadius = 16f * density;
            if (dx * dx + dy * dy <= hitRadius * hitRadius) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // Do not block Telegram's search, menu, or navigation controls.
            return touchedBulb(event.getX(), event.getY()) >= 0;
        }
        if (action == MotionEvent.ACTION_CANCEL) return false;
        if (action != MotionEvent.ACTION_UP) return true;
        int index = touchedBulb(event.getX(), event.getY());
        if (index < 0) return false;
        int outcome = random.nextInt(3);
        if (outcome == 0) bulbs[index] = random.nextInt(colors.length);
        else if (outcome == 1) {
            surprise = index;
            surpriseUntil = SystemClock.uptimeMillis() + 850;
        } else {
            for (int i = 0; i < bulbs.length; i++) bulbs[i] = random.nextInt(colors.length);
        }
        if (isSoundEnabled()) playBell();
        invalidate();
        return true;
    }

    private void playBell() {
        // Opt-in, brief system tone; no external media assets or permissions.
        ToneGenerator tone = null;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90);
        } catch (RuntimeException ignored) {
            // Sound must never crash the dialogs screen.
        } finally {
            if (tone != null) {
                final ToneGenerator releaseTone = tone;
                postDelayed(releaseTone::release, 160);
            }
        }
    }

}
