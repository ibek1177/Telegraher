package com.ibek.ghost;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

/**
 * История правок: перед тем как Telegram перезапишет отредактированное сообщение
 * в своей базе, сохраняем прежний текст в отдельную таблицу той же базы.
 * Всё выполняется в очереди базы данных (storageQueue), поэтому порядок
 * «сначала сохранить старое, потом записать новое» гарантирован.
 */
public class EditHistory {

    public static class Version {
        public final int date;
        public final String text;

        Version(int date, String text) {
            this.date = date;
            this.text = text;
        }
    }

    public interface Callback {
        void onLoaded(ArrayList<Version> versions);
    }

    private static final boolean[] tableReady = new boolean[64];

    private static void ensureTable(int account, SQLiteDatabase db) throws Exception {
        if (account >= 0 && account < tableReady.length && tableReady[account]) {
            return;
        }
        db.executeFast("CREATE TABLE IF NOT EXISTS ghost_edit_history(uid INTEGER, mid INTEGER, date INTEGER, message TEXT, PRIMARY KEY(uid, mid, date))").stepThis().dispose();
        if (account >= 0 && account < tableReady.length) {
            tableReady[account] = true;
        }
    }

    /** Вызывается до записи отредактированных сообщений в базу */
    public static void saveBeforeEdit(int account, long dialogId, ArrayList<TLRPC.Message> edited) {
        if (!Ghost.editHistory || edited == null || edited.isEmpty()) {
            return;
        }
        final ArrayList<TLRPC.Message> copy = new ArrayList<>(edited);
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase db = storage.getDatabase();
            if (db == null) {
                return;
            }
            try {
                ensureTable(account, db);
                for (int i = 0; i < copy.size(); i++) {
                    TLRPC.Message msg = copy.get(i);
                    if (msg == null) {
                        continue;
                    }
                    TLRPC.Message old = readOld(db, dialogId, msg.id);
                    if (old == null) {
                        continue;
                    }
                    String oldText = old.message == null ? "" : old.message;
                    String newText = msg.message == null ? "" : msg.message;
                    if (oldText.equals(newText) || TextUtils.isEmpty(oldText)) {
                        continue; // текст не менялся (например, обновились реакции) или это было фото без подписи
                    }
                    int date = old.edit_date != 0 ? old.edit_date : old.date;
                    SQLitePreparedStatement st = db.executeFast("REPLACE INTO ghost_edit_history VALUES(?, ?, ?, ?)");
                    st.requery();
                    st.bindLong(1, dialogId);
                    st.bindInteger(2, msg.id);
                    st.bindInteger(3, date);
                    st.bindString(4, oldText);
                    st.step();
                    st.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static TLRPC.Message readOld(SQLiteDatabase db, long dialogId, int mid) {
        SQLiteCursor cursor = null;
        try {
            cursor = db.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, dialogId));
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    return m;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return null;
    }

    /** Все сохранённые прежние версии сообщения, от старых к новым; результат — в главном потоке */
    public static void load(int account, long dialogId, int mid, Callback callback) {
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<Version> result = new ArrayList<>();
            SQLiteDatabase db = storage.getDatabase();
            SQLiteCursor cursor = null;
            try {
                if (db != null) {
                    ensureTable(account, db);
                    cursor = db.queryFinalized(String.format(Locale.US, "SELECT date, message FROM ghost_edit_history WHERE uid = %d AND mid = %d ORDER BY date ASC", dialogId, mid));
                    while (cursor.next()) {
                        result.add(new Version(cursor.intValue(0), cursor.stringValue(1)));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> callback.onLoaded(result));
        });
    }
}
