package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
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
            paint.setColor(colors[bulbs[i]]);
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
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return event.getActionMasked() == MotionEvent.ACTION_DOWN && event.getY() < 65 * getResources().getDisplayMetrics().density;
        }
        float density = getResources().getDisplayMetrics().density;
        if (event.getY() > 65f * density || getWidth() <= 0) return false;
        int index = Math.round(event.getX() / (getWidth() / (bulbs.length + 1f))) - 1;
        if (index < 0 || index >= bulbs.length) return false;
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
        // No sound assets or audio focus changes until the app integration is tested.
        // This deliberately remains silent even when opted in.
    }
}
