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

    public static volatile boolean enabled;
    public static volatile boolean hideRead;
    public static volatile boolean hideOnline;
    public static volatile boolean hideTyping;
    public static volatile boolean hideStories;
    public static volatile boolean hideContents;
    public static volatile boolean readOnReply;

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
            default: return false;
        }
    }

    public static void set(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
        load();
        if (key.equals(KEY_ENABLED) && value && hideOnline) {
            goOffline(-1, 0);
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
        if (!enabled || o == null) {
            return false;
        }

        if (o instanceof TL_account.updateStatus) {
            if (hideOnline) {
                ((TL_account.updateStatus) o).offline = true;
            }
            return false;
        }

        if (hideTyping && (o instanceof TLRPC.TL_messages_setTyping || o instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            return true;
        }

        if (hideRead) {
            if (o instanceof TLRPC.TL_messages_readHistory) {
                return !isReadAllowed(DialogObject.getPeerDialogId(((TLRPC.TL_messages_readHistory) o).peer));
            }
            if (o instanceof TLRPC.TL_channels_readHistory) {
                TLRPC.InputChannel ch = ((TLRPC.TL_channels_readHistory) o).channel;
                return ch == null || !isReadAllowed(-ch.channel_id);
            }
            if (o instanceof TLRPC.TL_messages_readDiscussion) {
                return !isReadAllowed(DialogObject.getPeerDialogId(((TLRPC.TL_messages_readDiscussion) o).peer));
            }
            if (o instanceof TLRPC.TL_messages_readEncryptedHistory) {
                return true;
            }
        }

        if (hideContents && (o instanceof TLRPC.TL_messages_readMessageContents || o instanceof TLRPC.TL_channels_readMessageContents)) {
            return true;
        }

        if (hideStories && (o instanceof TL_stories.TL_stories_readStories || o instanceof TL_stories.TL_stories_incrementStoryViews)) {
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
        if (hideRead && readOnReply && dialogId != 0) {
            AndroidUtilities.runOnUIThread(() -> readNow(account, dialogId), 600);
        }
        if (hideOnline) {
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
