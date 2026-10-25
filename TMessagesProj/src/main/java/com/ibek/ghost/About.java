package com.ibek.ghost;

import android.app.Activity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * «О программе» — список наших фишек и текущая версия.
 * Список пополняется здесь при добавлении новых возможностей.
 */
public class About {

    /** Пополняемый список возможностей нашей сборки */
    private static final String[] FEATURES = {
            "👻 Режим призрака: не отправлять «прочитано», «онлайн», «печатает», просмотры сторис, прослушивание голосовых",
            "👤 Режим призрака для отдельного чата: своё поведение для каждого человека",
            "✏️ История правок сообщений",
            "🗑 Сохранение удалённых сообщений (из Telegraher)",
            "📂 Скрытие вкладки «Все чаты»",
            "⬇️ Кнопка «Скачать видео» в чате — через «Сохранялку»",
            "⏰ Напоминания по сообщению",
            "🔄 Автообновление с окном «Что нового»",
            "📦 Установка APK из чатов без ошибок",
            "🏷 Своё название приложения в шапке (меняется в настройках призрака)",
            "💳 Быстрые карты/реквизиты: вставка в чат в один тап",
            "📌 Заметки по контакту: коротко в шапке чата, полностью в профиле",
            "🎨 Свои иконки приложения: Призрак (Фиолет/Неон/Мороз) + Монограмма iB",
            "🧮 Калькулятор-итого в поле ввода",
    };

    public static void show(BaseFragment fragment) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(LocaleController.formatString(R.string.GhostAboutVersion, Updater.versionName()));
        sb.append("\n\n");
        sb.append(LocaleController.getString(R.string.GhostAboutFeatures));
        sb.append('\n');
        for (String f : FEATURES) {
            sb.append('\n').append(f);
        }
        sb.append("\n\n");
        sb.append(LocaleController.getString(R.string.GhostAboutFooter));
        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.GhostAboutTitle))
                .setMessage(sb.toString())
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }
}
