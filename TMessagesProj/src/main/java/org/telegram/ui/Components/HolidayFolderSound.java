package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Optional, short, low-volume cue when changing dialog folders. */
public final class HolidayFolderSound {
    private static long lastPlayed;
    private HolidayFolderSound() {}

    public static void play(Context context) {
        final long now = android.os.SystemClock.uptimeMillis();
        if (now - lastPlayed < 400L) return;
        lastPlayed = now;
        ToneGenerator tone = null;
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return;
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 45);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 110);
            final ToneGenerator toRelease = tone;
            new Handler(Looper.getMainLooper()).postDelayed(toRelease::release, 200L);
        } catch (RuntimeException ignored) {
            if (tone != null) tone.release();
        }
    }
}
