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
        addExtra(context, list, Ghost.KEY_EDIT_HISTORY, R.string.GhostEditHistory, R.string.GhostEditHistoryInfo, false);

        TextInfoPrivacyCell extraInfo = new TextInfoPrivacyCell(context);
        extraInfo.setText(LocaleController.getString(R.string.GhostExtrasInfo));
        list.addView(extraInfo);

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

    /** Пока главный выключатель выключен, пункты бледные и не нажимаются */
    private void updateEnabled() {
        for (TextCheckCell cell : optionCells) {
            cell.setAlpha(Ghost.enabled ? 1f : 0.5f);
        }
    }
}
