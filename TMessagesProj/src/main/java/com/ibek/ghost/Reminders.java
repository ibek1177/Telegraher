package com.ibek.ghost;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/**
 * Напоминания по сообщению: в выбранное время прилетает уведомление с текстом.
 * Всё локально, через AlarmManager. Любая ошибка гасится, чтобы не ронять клиент.
 */
public class Reminders {

    private static final String CHANNEL = "ghost_reminders";
    public static final String ACTION = "com.ibek.ghost.REMINDER";

    public static void ensureChannel(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                    NotificationChannel ch = new NotificationChannel(CHANNEL,
                            LocaleController.getString(R.string.GhostReminderChannel), NotificationManager.IMPORTANCE_HIGH);
                    nm.createNotificationChannel(ch);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Запланировать напоминание через delayMs; text — что показать, link — куда вести (может быть null) */
    public static boolean schedule(Context ctx, long whenMs, String text, String link) {
        try {
            ensureChannel(ctx);
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return false;
            }
            int id = (int) (System.currentTimeMillis() & 0x7fffffff);
            Intent i = new Intent(ctx, ReminderReceiver.class)
                    .setAction(ACTION)
                    .putExtra("text", text == null ? "" : text)
                    .putExtra("link", link)
                    .putExtra("id", id);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            // неточный будильник: не требует разрешения SCHEDULE_EXACT_ALARM и работает в Doze
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, whenMs, pi);
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Вызывается из ReminderReceiver */
    public static void show(Context ctx, int id, String text, String link) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            Intent open;
            if (link != null && !link.isEmpty()) {
                open = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            } else {
                open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
                if (open == null) {
                    open = new Intent();
                }
            }
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            android.app.Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new android.app.Notification.Builder(ctx, CHANNEL);
            } else {
                b = new android.app.Notification.Builder(ctx);
            }
            String shownText = (text == null || text.isEmpty()) ? LocaleController.getString(R.string.GhostReminderEmpty) : text;
            b.setSmallIcon(R.drawable.notification)
                    .setContentTitle(LocaleController.getString(R.string.GhostReminderTitle))
                    .setContentText(shownText)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(shownText))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            nm.notify(id, b.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
