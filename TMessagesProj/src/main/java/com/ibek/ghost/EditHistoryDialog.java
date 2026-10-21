package com.ibek.ghost;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

/** Окно «История правок»: прежние версии сообщения и текущий текст */
public class EditHistoryDialog {

    public static void show(BaseFragment fragment, MessageObject message) {
        if (fragment == null || message == null || message.messageOwner == null) {
            return;
        }
        final int account = message.currentAccount;
        final long dialogId = message.getDialogId();
        final int mid = message.getId();
        final String current = message.messageOwner.message == null ? "" : message.messageOwner.message;
        final int currentDate = message.messageOwner.edit_date;

        EditHistory.load(account, dialogId, mid, versions -> {
            if (fragment.getParentActivity() == null) {
                return;
            }
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (versions.isEmpty()) {
                sb.append(LocaleController.getString(R.string.GhostEditHistoryEmpty));
            } else {
                for (int i = 0; i < versions.size(); i++) {
                    EditHistory.Version v = versions.get(i);
                    appendBlock(sb, LocaleController.formatString(R.string.GhostEditHistoryVersion, i + 1, LocaleController.formatDateTime(v.date, true)), v.text);
                }
                appendBlock(sb, LocaleController.formatString(R.string.GhostEditHistoryNow,
                        currentDate != 0 ? LocaleController.formatDateTime(currentDate, true) : ""), current);
            }
            AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(LocaleController.getString(R.string.GhostEditHistoryMenu))
                    .setMessage(sb)
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .create();
            fragment.showDialog(dialog);
        });
    }

    private static void appendBlock(SpannableStringBuilder sb, String header, String text) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        int start = sb.length();
        sb.append(header);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append('\n');
        sb.append(text == null || text.isEmpty() ? "—" : text);
    }
}
