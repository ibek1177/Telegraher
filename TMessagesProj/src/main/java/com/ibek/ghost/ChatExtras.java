package com.ibek.ghost;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Кнопки в меню сообщения: «Скачать» (через Сохранялку) и «Напомнить» */
public class ChatExtras {

    private static final String SAVER_PACKAGE = "app.saver";

    /** Установлена ли Сохранялка — чтобы не показывать «Скачать» тем, у кого её нет */
    public static boolean isSaverInstalled(Context ctx) {
        if (ctx == null) {
            return false;
        }
        try {
            ctx.getPackageManager().getPackageInfo(SAVER_PACKAGE, 0);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    // сайты, которые умеет Сохранялка (yt-dlp) — список не исчерпывающий, но покрывает частое
    private static final Pattern SUPPORTED = Pattern.compile(
            "(youtube\\.com|youtu\\.be|tiktok\\.com|instagram\\.com|instagr\\.am|twitter\\.com|x\\.com|vk\\.com|vk\\.ru|facebook\\.com|fb\\.watch|reddit\\.com|twitch\\.tv|soundcloud\\.com|pinterest\\.|likee\\.|rutube\\.ru|ok\\.ru|dzen\\.ru)",
            Pattern.CASE_INSENSITIVE);

    /** Первая ссылка на поддерживаемый сайт в тексте, или null */
    public static String findDownloadableLink(CharSequence text) {
        if (text == null) {
            return null;
        }
        try {
            Matcher m = URL.matcher(text);
            while (m.find()) {
                String url = m.group();
                if (SUPPORTED.matcher(url).find()) {
                    return url;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    /** Отправить ссылку в Сохранялку */
    public static void download(Activity activity, String link) {
        if (activity == null || link == null) {
            return;
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, link);
            send.setPackage(SAVER_PACKAGE);
            activity.startActivity(send);
        } catch (Throwable e) {
            // Сохранялка не установлена
            Toast.makeText(activity, LocaleController.getString(R.string.GhostSaverMissing), Toast.LENGTH_LONG).show();
        }
    }

    /** Диалог выбора времени напоминания */
    public static void remind(BaseFragment fragment, CharSequence messageText, String link) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Activity activity = fragment.getParentActivity();
        final String text = messageText == null ? "" : messageText.toString();

        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.GhostRemindHour),
                LocaleController.getString(R.string.GhostRemind3Hours),
                LocaleController.getString(R.string.GhostRemindEvening),
                LocaleController.getString(R.string.GhostRemindTomorrow),
        };
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.GhostRemindTitle));
        b.setItems(items, (dialog, which) -> {
            long when = computeWhen(which);
            boolean ok = Reminders.schedule(activity, when, text, link);
            Toast.makeText(activity,
                    LocaleController.getString(ok ? R.string.GhostRemindSet : R.string.GhostRemindFail),
                    Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private static long computeWhen(int which) {
        Calendar c = Calendar.getInstance();
        switch (which) {
            case 0: // через час
                c.add(Calendar.HOUR_OF_DAY, 1);
                break;
            case 1: // через 3 часа
                c.add(Calendar.HOUR_OF_DAY, 3);
                break;
            case 2: // вечером в 19:00 (или завтра вечером, если уже прошло)
                if (c.get(Calendar.HOUR_OF_DAY) >= 19) {
                    c.add(Calendar.DAY_OF_YEAR, 1);
                }
                c.set(Calendar.HOUR_OF_DAY, 19);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                break;
            default: // завтра в 10:00
                c.add(Calendar.DAY_OF_YEAR, 1);
                c.set(Calendar.HOUR_OF_DAY, 10);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                break;
        }
        return c.getTimeInMillis();
    }
}
