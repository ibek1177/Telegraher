package com.ibek.ghost;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import java.util.ArrayList;

/** Экран «Режим призрака»: главный выключатель и отдельные пункты */
public class GhostSettingsActivity extends BaseFragment {

    private final ArrayList<TextCheckCell> optionCells = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.GhostMode));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        // Главный выключатель
        TextCheckCell master = new TextCheckCell(context);
        master.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        master.setTextAndValueAndCheck(
                LocaleController.getString(R.string.GhostModeEnable),
                LocaleController.getString(R.string.GhostModeEnableInfo),
                Ghost.enabled, true, false);
        master.setOnClickListener(v -> {
            boolean value = !Ghost.enabled;
            Ghost.set(Ghost.KEY_ENABLED, value);
            master.setChecked(value);
            updateEnabled();
        });
        list.addView(master);

        TextInfoPrivacyCell masterInfo = new TextInfoPrivacyCell(context);
        masterInfo.setText(LocaleController.getString(R.string.GhostModeWarning));
        list.addView(masterInfo);

        HeaderCell header = new HeaderCell(context);
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        header.setText(LocaleController.getString(R.string.GhostModeWhat));
        list.addView(header);

        addOption(context, list, Ghost.KEY_READ, R.string.GhostHideRead, R.string.GhostHideReadInfo, true);
        addOption(context, list, Ghost.KEY_READ_ON_REPLY, R.string.GhostReadOnReply, R.string.GhostReadOnReplyInfo, true);
        addOption(context, list, Ghost.KEY_ONLINE, R.string.GhostHideOnline, R.string.GhostHideOnlineInfo, true);
        addOption(context, list, Ghost.KEY_TYPING, R.string.GhostHideTyping, R.string.GhostHideTypingInfo, true);
        addOption(context, list, Ghost.KEY_CONTENTS, R.string.GhostHideContents, R.string.GhostHideContentsInfo, true);
        addOption(context, list, Ghost.KEY_STORIES, R.string.GhostHideStories, R.string.GhostHideStoriesInfo, false);

        TextInfoPrivacyCell bottomInfo = new TextInfoPrivacyCell(context);
        bottomInfo.setText(LocaleController.getString(R.string.GhostModeManualRead));
        list.addView(bottomInfo);

        // Другие фишки — работают независимо от режима призрака
        HeaderCell extraHeader = new HeaderCell(context);
        extraHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        extraHeader.setText(LocaleController.getString(R.string.GhostExtras));
        list.addView(extraHeader);
        addExtra(context, list, Ghost.KEY_EDIT_HISTORY, R.string.GhostEditHistory, R.string.GhostEditHistoryInfo, true);
        addExtra(context, list, Ghost.KEY_HIDE_ALL_TAB, R.string.GhostHideAllTab, R.string.GhostHideAllTabInfo, false);

        TextInfoPrivacyCell extraInfo = new TextInfoPrivacyCell(context);
        extraInfo.setText(LocaleController.getString(R.string.GhostExtrasInfo));
        list.addView(extraInfo);

        // Название приложения — любой может вписать своё
        HeaderCell titleHeader = new HeaderCell(context);
        titleHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleHeader.setText(LocaleController.getString(R.string.GhostAppearance));
        list.addView(titleHeader);

        final org.telegram.ui.Cells.TextCell titleCell = new org.telegram.ui.Cells.TextCell(context);
        titleCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleCell.setTextAndValueAndIcon(LocaleController.getString(R.string.GhostAppTitle), Ghost.appTitle(), R.drawable.msg_edit, false);
        titleCell.setOnClickListener(v -> showAppTitleDialog(context, titleCell));
        list.addView(titleCell);

        TextInfoPrivacyCell titleInfo = new TextInfoPrivacyCell(context);
        titleInfo.setText(LocaleController.getString(R.string.GhostAppTitleInfo));
        list.addView(titleInfo);

        // Карты (реквизиты)
        HeaderCell cardsHeader = new HeaderCell(context);
        cardsHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cardsHeader.setText(LocaleController.getString(R.string.GhostCardsTitle));
        list.addView(cardsHeader);

        final org.telegram.ui.Cells.TextCell[] cardCells = new org.telegram.ui.Cells.TextCell[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            org.telegram.ui.Cells.TextCell c = new org.telegram.ui.Cells.TextCell(context);
            c.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            cardCells[idx] = c;
            refreshCardCell(c, idx);
            c.setOnClickListener(v -> showCardEditDialog(context, cardCells[idx], idx));
            list.addView(c);
        }

        TextInfoPrivacyCell cardsInfo = new TextInfoPrivacyCell(context);
        cardsInfo.setText(LocaleController.getString(R.string.GhostCardsInfo));
        list.addView(cardsInfo);

        // О программе
        org.telegram.ui.Cells.TextCell aboutCell = new org.telegram.ui.Cells.TextCell(context);
        aboutCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        aboutCell.setTextAndIcon(LocaleController.getString(R.string.GhostAboutTitle), R.drawable.msg_info, false);
        aboutCell.setOnClickListener(v -> com.ibek.ghost.About.show(this));
        list.addView(aboutCell);

        updateEnabled();

        fragmentView = scroll;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        return fragmentView;
    }

    private void addOption(Context context, LinearLayout list, String key, int title, int info, boolean divider) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValueAndCheck(
                LocaleController.getString(title),
                LocaleController.getString(info),
                Ghost.get(key), true, divider);
        cell.setOnClickListener(v -> {
            if (!Ghost.enabled) {
                return;
            }
            boolean value = !Ghost.get(key);
            Ghost.set(key, value);
            cell.setChecked(value);
        });
        optionCells.add(cell);
        list.addView(cell);
    }

    private void addExtra(Context context, LinearLayout list, String key, int title, int info, boolean divider) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValueAndCheck(
                LocaleController.getString(title),
                LocaleController.getString(info),
                Ghost.get(key), true, divider);
        cell.setOnClickListener(v -> {
            boolean value = !Ghost.get(key);
            Ghost.set(key, value);
            cell.setChecked(value);
        });
        list.addView(cell);
    }

    /** Диалог ввода своего названия приложения */
    private void showAppTitleDialog(Context context, org.telegram.ui.Cells.TextCell cell) {
        final org.telegram.ui.Components.EditTextBoldCursor et = new org.telegram.ui.Components.EditTextBoldCursor(context);
        et.setText(Ghost.appTitle());
        try {
            et.setSelection(et.getText().length());
        } catch (Throwable ignore) {
        }
        et.setHint(Ghost.DEFAULT_APP_TITLE);
        et.setSingleLine(true);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setCursorSize(org.telegram.messenger.AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setPadding(org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(10), org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(10));

        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(R.string.GhostAppTitle));
        b.setView(et);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String v = et.getText() == null ? "" : et.getText().toString();
            Ghost.setAppTitle(v);
            cell.setTextAndValueAndIcon(LocaleController.getString(R.string.GhostAppTitle), Ghost.appTitle(), R.drawable.msg_edit, false);
        });
        b.setNeutralButton(LocaleController.getString(R.string.GhostAppTitleReset), (d, w) -> {
            Ghost.setAppTitle("");
            cell.setTextAndValueAndIcon(LocaleController.getString(R.string.GhostAppTitle), Ghost.appTitle(), R.drawable.msg_edit, false);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        try {
            et.requestFocus();
            org.telegram.messenger.AndroidUtilities.showKeyboard(et);
        } catch (Throwable ignore) {
        }
    }

    private void refreshCardCell(org.telegram.ui.Cells.TextCell cell, int i) {
        String label = Ghost.cardLabel(i);
        String number = Ghost.cardNumber(i);
        String title = (label == null || label.isEmpty()) ? ("Карта " + (i + 1)) : label;
        String value = (number == null || number.isEmpty()) ? LocaleController.getString(R.string.GhostCardEmpty) : number;
        cell.setTextAndValueAndIcon(title, value, R.drawable.msg_copy, i < 2);
    }

    private org.telegram.ui.Components.EditTextBoldCursor makeCardField(Context context, String value, int hintRes, boolean numberMono) {
        org.telegram.ui.Components.EditTextBoldCursor et = new org.telegram.ui.Components.EditTextBoldCursor(context);
        et.setText(value == null ? "" : value);
        et.setHint(LocaleController.getString(hintRes));
        et.setSingleLine(true);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setCursorSize(org.telegram.messenger.AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        if (numberMono) {
            et.setTypeface(android.graphics.Typeface.MONOSPACE);
            et.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        }
        try {
            et.setSelection(et.getText().length());
        } catch (Throwable ignore) {
        }
        return et;
    }

    private static String fieldText(org.telegram.ui.Components.EditTextBoldCursor et) {
        return et == null || et.getText() == null ? "" : et.getText().toString();
    }

    private void showCardEditDialog(Context context, org.telegram.ui.Cells.TextCell cell, int i) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(22);
        box.setPadding(pad, org.telegram.messenger.AndroidUtilities.dp(6), pad, 0);

        final org.telegram.ui.Components.EditTextBoldCursor labelEt = makeCardField(context, Ghost.cardLabel(i), R.string.GhostCardLabelHint, false);
        final org.telegram.ui.Components.EditTextBoldCursor numberEt = makeCardField(context, Ghost.cardNumber(i), R.string.GhostCardNumberHint, true);
        final org.telegram.ui.Components.EditTextBoldCursor holderEt = makeCardField(context, Ghost.cardHolder(i), R.string.GhostCardHolderHint, false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = org.telegram.messenger.AndroidUtilities.dp(8);
        box.addView(labelEt, lp);
        box.addView(numberEt, lp);
        box.addView(holderEt, lp);

        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        b.setTitle("Карта " + (i + 1));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            Ghost.setCard(i, fieldText(labelEt), fieldText(numberEt), fieldText(holderEt));
            refreshCardCell(cell, i);
        });
        b.setNeutralButton(LocaleController.getString(R.string.Clear), (d, w) -> {
            Ghost.setCard(i, "", "", "");
            refreshCardCell(cell, i);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        try {
            numberEt.requestFocus();
            org.telegram.messenger.AndroidUtilities.showKeyboard(numberEt);
        } catch (Throwable ignore) {
        }
    }

    /** Пока главный выключатель выключен, пункты бледные и не нажимаются */
    private void updateEnabled() {
        for (TextCheckCell cell : optionCells) {
            cell.setAlpha(Ghost.enabled ? 1f : 0.5f);
        }
    }
}
