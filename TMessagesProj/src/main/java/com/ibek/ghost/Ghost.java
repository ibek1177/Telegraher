package com.ibek.ghost;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Режим призрака. Все запросы к серверам Telegram проходят через
 * ConnectionsManager.sendRequestInternal, там вызывается {@link #filter}.
 * Что можно — не отправляем, «онлайн» подменяем на «не в сети».
 */
public class Ghost {

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_READ = "hide_read";
    public static final String KEY_ONLINE = "hide_online";
    public static final String KEY_TYPING = "hide_typing";
    public static final String KEY_STORIES = "hide_stories";
    public static final String KEY_CONTENTS = "hide_contents";
    public static final String KEY_READ_ON_REPLY = "read_on_reply";
    public static final String KEY_HIDE_ALL_TAB = "hide_all_tab";
    public static final String KEY_EDIT_HISTORY = "edit_history";
    public static final String KEY_APP_TITLE = "app_title";
    public static final String DEFAULT_APP_TITLE = "iBekgram";

    public static volatile boolean enabled;
    public static volatile boolean hideRead;
    public static volatile boolean hideOnline;
    public static volatile boolean hideTyping;
    public static volatile boolean hideStories;
    public static volatile boolean hideContents;
    public static volatile boolean readOnReply;
    public static volatile boolean hideAllTab;
    public static volatile boolean editHistory;
    /** Название приложения в шапке (любое, что впишет пользователь) */
    public static volatile String appTitle = DEFAULT_APP_TITLE;

    /** Чаты, которые пользователь сам отметил прочитанными: dialogId → до какого момента разрешено */
    private static final ConcurrentHashMap<Long, Long> allowedRead = new ConcurrentHashMap<>();

    static {
        load();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ghost_mode", Context.MODE_PRIVATE);
    }

    public static void load() {
        SharedPreferences p = prefs();
        enabled = p.getBoolean(KEY_ENABLED, false);
        hideRead = p.getBoolean(KEY_READ, true);
        hideOnline = p.getBoolean(KEY_ONLINE, true);
        hideTyping = p.getBoolean(KEY_TYPING, true);
        hideStories = p.getBoolean(KEY_STORIES, true);
        hideContents = p.getBoolean(KEY_CONTENTS, true);
        readOnReply = p.getBoolean(KEY_READ_ON_REPLY, true);
        hideAllTab = p.getBoolean(KEY_HIDE_ALL_TAB, false);
        editHistory = p.getBoolean(KEY_EDIT_HISTORY, true);
        String t = p.getString(KEY_APP_TITLE, DEFAULT_APP_TITLE);
        appTitle = (t == null || t.trim().isEmpty()) ? DEFAULT_APP_TITLE : t;
    }

    /** Текущее название для шапки — никогда не пустое */
    public static String appTitle() {
        String t = appTitle;
        return (t == null || t.trim().isEmpty()) ? DEFAULT_APP_TITLE : t;
    }

    /** Сменить название приложения в шапке. Пустая строка вернёт значение по умолчанию. */
    public static void setAppTitle(String v) {
        if (v == null) {
            v = "";
        }
        v = v.trim();
        prefs().edit().putString(KEY_APP_TITLE, v).apply();
        load();
    }

    // ---- Карты (реквизиты): до 3 штук, i = 0..2 ----

    public static String cardLabel(int i) {
        return prefs().getString("card" + i + "_label", "");
    }

    public static String cardNumber(int i) {
        return prefs().getString("card" + i + "_number", "");
    }

    public static String cardHolder(int i) {
        return prefs().getString("card" + i + "_holder", "");
    }

    /** Есть ли у слота номер карты */
    public static boolean hasCard(int i) {
        String n = cardNumber(i);
        return n != null && !n.trim().isEmpty();
    }

    public static void setCard(int i, String label, String number, String holder) {
        prefs().edit()
                .putString("card" + i + "_label", label == null ? "" : label.trim())
                .putString("card" + i + "_number", number == null ? "" : number.trim())
                .putString("card" + i + "_holder", holder == null ? "" : holder.trim())
                .apply();
    }

    /** Текст для вставки: номер + (ФИО получателя новой строкой, если задано) */
    public static String cardInsertText(int i) {
        String number = cardNumber(i);
        number = number == null ? "" : number.trim();
        String holder = cardHolder(i);
        if (holder != null && !holder.trim().isEmpty()) {
            return number + "\n" + holder.trim();
        }
        return number;
    }

    public static boolean get(String key) {
        switch (key) {
            case KEY_ENABLED: return enabled;
            case KEY_READ: return hideRead;
            case KEY_ONLINE: return hideOnline;
            case KEY_TYPING: return hideTyping;
            case KEY_STORIES: return hideStories;
            case KEY_CONTENTS: return hideContents;
            case KEY_READ_ON_REPLY: return readOnReply;
            case KEY_HIDE_ALL_TAB: return hideAllTab;
            case KEY_EDIT_HISTORY: return editHistory;
            default: return false;
        }
    }

    // Призрак по людям: режим для конкретного чата. 0 — как глобально, 1 — всегда скрывать, 2 — никогда.
    public static final int CHAT_DEFAULT = 0;
    public static final int CHAT_ALWAYS = 1;
    public static final int CHAT_NEVER = 2;

    private static final java.util.concurrent.ConcurrentHashMap<Long, Integer> chatModes = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean chatModesLoaded;

    private static void loadChatModes() {
        if (chatModesLoaded) {
            return;
        }
        synchronized (chatModes) {
            if (chatModesLoaded) {
                return;
            }
            String raw = prefs().getString("chat_modes", "");
            for (String part : raw.split(";")) {
                if (part.isEmpty()) continue;
                int c = part.indexOf(':');
                if (c <= 0) continue;
                try {
                    chatModes.put(Long.parseLong(part.substring(0, c)), Integer.parseInt(part.substring(c + 1)));
                } catch (Throwable ignore) {
                }
            }
            chatModesLoaded = true;
        }
    }

    public static int chatMode(long dialogId) {
        loadChatModes();
        Integer m = chatModes.get(dialogId);
        return m == null ? CHAT_DEFAULT : m;
    }

    public static void setChatMode(long dialogId, int mode) {
        loadChatModes();
        if (mode == CHAT_DEFAULT) {
            chatModes.remove(dialogId);
        } else {
            chatModes.put(dialogId, mode);
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Long, Integer> e : chatModes.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        prefs().edit().putString("chat_modes", sb.toString()).apply();
    }

    /** Действует ли призрак для этого чата с учётом персонального режима */
    private static boolean ghostFor(long dialogId) {
        switch (chatMode(dialogId)) {
            case CHAT_ALWAYS: return true;
            case CHAT_NEVER: return false;
            default: return enabled;
        }
    }

    public static void set(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
        load();
        if (key.equals(KEY_ENABLED) && value && hideOnline) {
            goOffline(-1, 0);
        }
        if (key.equals(KEY_HIDE_ALL_TAB)) {
            // перерисовать панель папок во всех аккаунтах
            for (int a : org.telegram.messenger.SharedConfig.activeAccounts) {
                org.telegram.messenger.NotificationCenter.getInstance(a).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
            }
        }
    }

    /** Разрешить отправить «прочитано» в этот чат (ручное действие пользователя) */
    public static void allowRead(long dialogId) {
        allowedRead.put(dialogId, SystemClock.elapsedRealtime() + 20_000);
    }

    private static boolean isReadAllowed(long dialogId) {
        Long until = allowedRead.get(dialogId);
        return until != null && until > SystemClock.elapsedRealtime();
    }

    /**
     * Вызывается перед отправкой каждого запроса.
     * @return true — запрос не отправлять
     */
    public static boolean filter(int account, TLObject o) {
        if (o == null) {
            return false;
        }

        // Онлайн — это глобальный статус, скрываем только при общем призраке
        if (o instanceof TL_account.updateStatus) {
            if (enabled && hideOnline) {
                ((TL_account.updateStatus) o).offline = true;
            }
            return false;
        }

        // «печатает» — с учётом персонального режима чата
        if (o instanceof TLRPC.TL_messages_setTyping) {
            long d = DialogObject.getPeerDialogId(((TLRPC.TL_messages_setTyping) o).peer);
            return hideTyping && ghostFor(d);
        }
        if (o instanceof TLRPC.TL_messages_setEncryptedTyping) {
            return enabled && hideTyping;
        }

        // «прочитано» — с учётом персонального режима чата
        if (o instanceof TLRPC.TL_messages_readHistory) {
            long d = DialogObject.getPeerDialogId(((TLRPC.TL_messages_readHistory) o).peer);
            return hideRead && ghostFor(d) && !isReadAllowed(d);
        }
        if (o instanceof TLRPC.TL_channels_readHistory) {
            TLRPC.InputChannel ch = ((TLRPC.TL_channels_readHistory) o).channel;
            long d = ch == null ? 0 : -ch.channel_id;
            return hideRead && ghostFor(d) && !isReadAllowed(d);
        }
        if (o instanceof TLRPC.TL_messages_readDiscussion) {
            long d = DialogObject.getPeerDialogId(((TLRPC.TL_messages_readDiscussion) o).peer);
            return hideRead && ghostFor(d) && !isReadAllowed(d);
        }
        if (o instanceof TLRPC.TL_messages_readEncryptedHistory) {
            return enabled && hideRead;
        }

        // Прослушивание голосовых/кружков и просмотры сторис — глобально
        if (enabled && hideContents && (o instanceof TLRPC.TL_messages_readMessageContents || o instanceof TLRPC.TL_channels_readMessageContents)) {
            return true;
        }
        if (enabled && hideStories && (o instanceof TL_stories.TL_stories_readStories || o instanceof TL_stories.TL_stories_incrementStoryViews)) {
            return true;
        }

        afterSend(account, o);
        return false;
    }

    /** Отправка сообщения: при необходимости отмечаем чат прочитанным и снова уходим в оффлайн */
    private static void afterSend(int account, TLObject o) {
        TLRPC.InputPeer peer = null;
        if (o instanceof TLRPC.TL_messages_sendMessage) {
            peer = ((TLRPC.TL_messages_sendMessage) o).peer;
        } else if (o instanceof TLRPC.TL_messages_sendMedia) {
            peer = ((TLRPC.TL_messages_sendMedia) o).peer;
        } else if (o instanceof TLRPC.TL_messages_sendMultiMedia) {
            peer = ((TLRPC.TL_messages_sendMultiMedia) o).peer;
        } else if (o instanceof TLRPC.TL_messages_forwardMessages) {
            peer = ((TLRPC.TL_messages_forwardMessages) o).to_peer;
        }
        if (peer == null) {
            return;
        }
        long dialogId = DialogObject.getPeerDialogId(peer);
        if (hideRead && readOnReply && dialogId != 0 && ghostFor(dialogId)) {
            AndroidUtilities.runOnUIThread(() -> readNow(account, dialogId), 600);
        }
        if (enabled && hideOnline) {
            goOffline(account, 1500);
        }
    }

    /** Отправить «прочитано» в чат до последнего сообщения, минуя фильтр */
    public static void readNow(int account, long dialogId) {
        MessagesController mc = MessagesController.getInstance(account);
        TLRPC.Dialog dialog = mc.dialogs_dict.get(dialogId);
        if (dialog == null || dialog.top_message == 0) {
            return;
        }
        allowRead(dialogId);
        TLObject req;
        if (DialogObject.isChatDialog(dialogId) && ChatHelper.isChannel(mc, dialogId)) {
            TLRPC.TL_channels_readHistory r = new TLRPC.TL_channels_readHistory();
            r.channel = mc.getInputChannel(-dialogId);
            r.max_id = dialog.top_message;
            req = r;
        } else {
            TLRPC.TL_messages_readHistory r = new TLRPC.TL_messages_readHistory();
            r.peer = mc.getInputPeer(dialogId);
            r.max_id = dialog.top_message;
            req = r;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (RequestDelegate) null);
    }

    /** Отправить «не в сети»; account = -1 — для всех аккаунтов */
    private static void goOffline(int account, long delay) {
        AndroidUtilities.runOnUIThread(() -> {
            if (account >= 0) {
                sendOffline(account);
            } else {
                for (int a : org.telegram.messenger.SharedConfig.activeAccounts) {
                    if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                        sendOffline(a);
                    }
                }
            }
        }, delay);
    }

    private static void sendOffline(int account) {
        TL_account.updateStatus req = new TL_account.updateStatus();
        req.offline = true;
        ConnectionsManager.getInstance(account).sendRequest(req, (RequestDelegate) null);
    }

    /** Небольшой помощник, чтобы не тянуть ChatObject в фильтр */
    private static class ChatHelper {
        static boolean isChannel(MessagesController mc, long dialogId) {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            return org.telegram.messenger.ChatObject.isChannel(chat);
        }
    }
}
