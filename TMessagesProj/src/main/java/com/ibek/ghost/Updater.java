package com.ibek.ghost;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Проверка обновлений через релизы на GitHub.
 * Версию и время сборки CI записывает в assets/ghost_build.txt.
 */
public class Updater {

    private static final String REPO = "ibek1177/Telegraher";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;

    private static String buildVersion;   // например «1.2-ghost»; null — локальная сборка
    private static long buildTime;        // секунды, когда собрана эта версия
    private static String buildChangelog = "";  // список изменений этой версии
    private static boolean checking;

    static {
        try (InputStream in = ApplicationLoader.applicationContext.getAssets().open("ghost_build.txt")) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            buildVersion = r.readLine();
            String t = r.readLine();
            buildTime = t != null ? Long.parseLong(t.trim()) : 0;
            if (buildVersion != null) {
                buildVersion = buildVersion.trim();
            }
            StringBuilder cl = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (cl.length() > 0) cl.append('\n');
                cl.append(line);
            }
            buildChangelog = cl.toString().trim();
        } catch (Exception e) {
            buildVersion = null;
        }
    }

    /** При первом запуске новой версии — окно «Что нового» */
    public static void maybeShowWhatsNew(Activity activity) {
        try {
            if (buildVersion == null || activity == null || activity.isFinishing()) {
                return;
            }
            String shown = prefs().getString("whatsnew_shown", "");
            if (buildVersion.equals(shown)) {
                return;
            }
            prefs().edit().putString("whatsnew_shown", buildVersion).apply();
            if (shown.isEmpty()) {
                // первая установка — без окна, просто запоминаем версию
                return;
            }
            String text = buildChangelog.isEmpty()
                    ? LocaleController.getString(R.string.GhostWhatsNewEmpty)
                    : buildChangelog;
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.formatString(R.string.GhostWhatsNewTitle, buildVersion))
                    .setMessage(text)
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String versionName() {
        return buildVersion != null ? buildVersion : LocaleController.getString(R.string.GhostUpdateLocalBuild);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ghost_updater", Context.MODE_PRIVATE);
    }

    /** Тихая проверка при запуске: не чаще раза в 6 часов, об одной версии напоминаем раз в сутки */
    public static void checkOnStart(Activity activity) {
        if (buildVersion == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - prefs().getLong("last_check", 0) < CHECK_INTERVAL) {
            return;
        }
        check(activity, false);
    }

    /** manual = true — пользователь нажал «Проверить обновления» */
    public static void check(Activity activity, boolean manual) {
        if (checking) {
            return;
        }
        if (buildVersion == null && manual) {
            Toast.makeText(activity, LocaleController.getString(R.string.GhostUpdateLocalBuildInfo), Toast.LENGTH_LONG).show();
            return;
        }
        checking = true;
        Utilities.globalQueue.postRunnable(() -> {
            Release rel = null;
            try {
                rel = fetchLatest();
            } catch (Exception e) {
                FileLog.e(e);
            }
            final Release release = rel;
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                prefs().edit().putLong("last_check", System.currentTimeMillis()).apply();
                if (activity.isFinishing()) {
                    return;
                }
                if (release == null) {
                    if (manual) {
                        Toast.makeText(activity, LocaleController.getString(R.string.GhostUpdateError), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!isNewer(release)) {
                    if (manual) {
                        Toast.makeText(activity, LocaleController.getString(R.string.GhostUpdateLatest), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!manual) {
                    String key = "shown_" + release.tag;
                    long shown = prefs().getLong(key, 0);
                    if (System.currentTimeMillis() - shown < 24 * 60 * 60 * 1000L) {
                        return;
                    }
                    prefs().edit().putLong(key, System.currentTimeMillis()).apply();
                }
                showDialog(activity, release);
            });
        });
    }

    private static boolean isNewer(Release r) {
        String ownTag = "graher_" + buildVersion;
        return !ownTag.equals(r.tag) && r.publishedAt > buildTime;
    }

    private static void showDialog(Activity activity, Release r) {
        String text = LocaleController.formatString(R.string.GhostUpdateText, r.name, versionName());
        if (r.notes != null && !r.notes.isEmpty()) {
            text += "\n\n" + r.notes;
        }
        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.GhostUpdateTitle))
                .setMessage(text)
                .setPositiveButton(LocaleController.getString(R.string.GhostUpdateDownload), (dialog, which) ->
                        // системный браузер скачает APK и сам предложит установить;
                        // внутренний загрузчик Telegram на APK застревает
                        Browser.openUrlInSystemBrowser(activity, r.apkUrl != null ? r.apkUrl : r.pageUrl))
                .setNegativeButton(LocaleController.getString(R.string.GhostUpdateLater), null)
                .show();
    }

    private static class Release {
        String tag;
        String name;
        String notes;
        String pageUrl;
        String apkUrl;
        long publishedAt;
    }

    private static Release fetchLatest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Telegraher-Ghost");
        try {
            if (c.getResponseCode() != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            JSONObject o = new JSONObject(sb.toString());
            Release rel = new Release();
            rel.tag = o.optString("tag_name");
            rel.name = o.optString("name", rel.tag);
            rel.pageUrl = o.optString("html_url");
            rel.notes = cleanNotes(o.optString("body", ""));
            rel.publishedAt = parseDate(o.optString("published_at"));
            JSONArray assets = o.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) {
                        rel.apkUrl = a.optString("browser_download_url");
                        break;
                    }
                }
            }
            return rel.tag.isEmpty() ? null : rel;
        } finally {
            c.disconnect();
        }
    }

    /** Убираем строку с sha256 из описания релиза, оставляем человеческий текст */
    private static String cleanNotes(String body) {
        StringBuilder out = new StringBuilder();
        for (String line : body.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.contains("sha256")) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(l);
        }
        String s = out.toString();
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    private static long parseDate(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso);
            return d != null ? d.getTime() / 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
