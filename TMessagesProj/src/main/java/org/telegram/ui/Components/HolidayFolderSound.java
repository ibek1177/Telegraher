package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;

/** A soft, synthesized festive bell. No external audio file or permission needed. */
public final class HolidayFolderSound {
    private static final int SAMPLE_RATE = 22050;
    private static long lastPlayed;
    private HolidayFolderSound() {}

    public static void play(Context context) {
        final long now = SystemClock.uptimeMillis();
        if (now - lastPlayed < 400L) return;
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null || manager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL
                || manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return;
        lastPlayed = now;
        // Generate off the UI thread so folder navigation stays responsive.
        new Thread(() -> {
            AudioTrack track = null;
            try {
                int samples = (int)(SAMPLE_RATE * 0.42);
                short[] pcm = new short[samples];
                for (int i = 0; i < samples; i++) {
                    double t = i / (double) SAMPLE_RATE;
                    double attack = Math.min(1.0, t / 0.009);
                    double envelope = attack * Math.exp(-t * 10.0);
                    double shimmer = Math.sin(2 * Math.PI * 1318.51 * t) * 0.60
                            + Math.sin(2 * Math.PI * 2637.02 * t) * 0.25
                            + Math.sin(2 * Math.PI * 3955.53 * t) * 0.15;
                    pcm[i] = (short)(Math.max(-1, Math.min(1, shimmer * envelope * 0.23)) * 32767);
                }
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(samples * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep(500);
            } catch (Exception ignored) {
                // Never interrupt folder navigation if audio is unavailable.
            } finally {
                if (track != null) {
                    try { track.stop(); } catch (IllegalStateException ignored) {}
                    track.release();
                }
            }
        }, "HolidayBell").start();
    }
}
