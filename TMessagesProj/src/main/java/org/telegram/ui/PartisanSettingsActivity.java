/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PartisanSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int rowCount;

    private int versionRow;
    private int versionDetailRow;
    private int idRow;
    private int idDetailRow;
    private int disableAvatarRow;
    private int disableAvatarDetailRow;
    private int renameChatRow;
    private int renameChatDetailRow;
    private int deleteMyMessagesRow;
    private int deleteMyMessagesDetailRow;
    private int deleteAfterReadRow;
    private int deleteAfterReadDetailRow;
    private int savedChannelsRow;
    private int savedChannelsDetailRow;
    private int reactionsRow;
    private int reactionsDetailRow;
    private int foreignAgentsRow;
    private int foreignAgentsDetailRow;
    private int onScreenLockActionRow;
    private int onScreenLockActionDetailRow;
    private int showUpdatesRow;
    private int showUpdatesDetailRow;
    private int showCallButtonRow;
    private int showCallButtonDetailRow;

    private class DangerousSettingSwitcher {
        public Context context;
        public View view;
        public boolean value;
        public Consumer<Boolean> setValue;
        public Consumer<UserConfig> dangerousAction;
        public Function<UserConfig, Boolean> isChanged;
        public String dangerousActionTitle;
        public String positiveButtonText;
        public String negativeButtonText;
        public String neutralButtonText;

        public void switchSetting() {
            if (context == null || !value || !isChangedSetting(isChanged)) {
                changeSetting(value);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(dangerousActionTitle);
                builder.setPositiveButton(positiveButtonText, (dialog, which) -> changeSetting(true));
                builder.setNegativeButton(negativeButtonText, (dialog, which) -> changeSetting(false));
                builder.setNeutralButton(neutralButtonText, null);
                showDialog(builder.create());
            }
        }

        private void changeSetting(boolean runDangerousAction) {
            setValue.accept(!value);
            SharedConfig.saveConfig();
            ((TextCheckCell) view).setChecked(!value);
            if (runDangerousAction) {
                foreachActivatedConfig(dangerousAction);
            }
        }

        private boolean isChangedSetting(Function<UserConfig, Boolean> isChanged) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig config = UserConfig.getInstance(a);
                if (config.isClientActivated()) {
                    if (isChanged.apply(config)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public PartisanSettingsActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString("PartisanSettings", R.string.PartisanSettings));
        frameLayout.setTag(Theme.key_windowBackgroundGray);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position == versionRow) {
                SharedConfig.showVersion = !SharedConfig.showVersion;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showVersion);
            } else if (position == idRow) {
                SharedConfig.showId = !SharedConfig.showId;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showId);
            } else if (position == disableAvatarRow) {
                DangerousSettingSwitcher switcher = new DangerousSettingSwitcher();
                switcher.context = context;
                switcher.view = view;
                switcher.value = SharedConfig.allowDisableAvatar;
                switcher.setValue = v -> SharedConfig.allowDisableAvatar = v;
                switcher.isChanged = c -> c.chatInfoOverrides.values().stream().anyMatch(o -> !o.avatarEnabled);
                switcher.dangerousActionTitle = LocaleController.getString("ResetChangedAvatarsTitle", R.string.ResetChangedAvatarsTitle);
                switcher.positiveButtonText = LocaleController.getString("Reset", R.string.Reset);
                switcher.negativeButtonText = LocaleController.getString("NotReset", R.string.NotReset);
                switcher.neutralButtonText = LocaleController.getString("Cancel", R.string.Cancel);
                switcher.dangerousAction = config -> {
                    for (UserConfig.ChatInfoOverride override : config.chatInfoOverrides.values()) {
                        override.avatarEnabled = true;
                    }
                };
                switcher.switchSetting();
            } else if (position == renameChatRow) {
                DangerousSettingSwitcher switcher = new DangerousSettingSwitcher();
                switcher.context = context;
                switcher.view = view;
                switcher.value = SharedConfig.allowRenameChat;
                switcher.setValue = v -> SharedConfig.allowRenameChat = v;
                switcher.isChanged = c -> c.chatInfoOverrides.values().stream().anyMatch(o -> o.title != null);
                switcher.dangerousActionTitle = LocaleController.getString("ResetChangedTitlesTitle", R.string.ResetChangedTitlesTitle);
                switcher.positiveButtonText = LocaleController.getString("Reset", R.string.Reset);
                switcher.negativeButtonText = LocaleController.getString("NotReset", R.string.NotReset);
                switcher.neutralButtonText = LocaleController.getString("Cancel", R.string.Cancel);
                switcher.dangerousAction = config -> {
                    for (UserConfig.ChatInfoOverride override : config.chatInfoOverrides.values()) {
                        override.title = null;
                    }
                };
                switcher.switchSetting();
            } else if (position == deleteMyMessagesRow) {
                SharedConfig.showDeleteMyMessages = !SharedConfig.showDeleteMyMessages;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showDeleteMyMessages);
            } else if (position == deleteAfterReadRow) {
                SharedConfig.showDeleteAfterRead = !SharedConfig.showDeleteAfterRead;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showDeleteAfterRead);
            } else if (position == savedChannelsRow) {
                DangerousSettingSwitcher switcher = new DangerousSettingSwitcher();
                switcher.context = context;
                switcher.view = view;
                switcher.value = SharedConfig.showSavedChannels;
                switcher.setValue = (value) -> {
                    SharedConfig.showSavedChannels = value;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.savedChannelsButtonStateChanged);
                };
                switcher.isChanged = config -> {
                    List<String> savedChannels = Arrays.asList(config.defaultChannels.split(","));

                    if (savedChannels.size() != config.savedChannels.size() || !savedChannels.containsAll(config.savedChannels)) {
                        return true;
                    }
                    return !savedChannels.equals(config.pinnedSavedChannels);
                };
                switcher.dangerousActionTitle = LocaleController.getString("ClearSavedChannelsTitle", R.string.ClearSavedChannelsTitle);
                switcher.positiveButtonText = LocaleController.getString("ClearButton", R.string.ClearButton);
                switcher.negativeButtonText = LocaleController.getString("NotClear", R.string.NotClear);
                switcher.neutralButtonText = LocaleController.getString("Cancel", R.string.Cancel);
                switcher.dangerousAction = (config) -> {
                    List<String> savedChannels = Arrays.asList(config.defaultChannels.split(","));
                    config.savedChannels = new HashSet<>(savedChannels);
                    config.pinnedSavedChannels = new ArrayList<>(savedChannels);
                };
                switcher.switchSetting();
            } else if (position == reactionsRow) {
                SharedConfig.allowReactions = !SharedConfig.allowReactions;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.allowReactions);
            } else if (position == foreignAgentsRow) {
                SharedConfig.cutForeignAgentsText = !SharedConfig.cutForeignAgentsText;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.cutForeignAgentsText);
            } else if (position == onScreenLockActionRow) {
                AlertsCreator.showOnScreenLockActionsAlert(this, getParentActivity(), () -> listAdapter.notifyDataSetChanged(), null);
            } else if (position == showUpdatesRow) {
                SharedConfig.toggleShowUpdates();
                ((TextCheckCell) view).setChecked(SharedConfig.showUpdates);
            } else if (position == showCallButtonRow) {
                SharedConfig.toggleShowCallButton();
                ((TextCheckCell) view).setChecked(SharedConfig.showCallButton);
            }
        });

        return fragmentView;
    }

    private static void foreachActivatedConfig(Consumer<UserConfig> action) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated()) {
                action.accept(config);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateRows() {
        rowCount = 0;

        versionRow = rowCount++;
        versionDetailRow = rowCount++;
        idRow = rowCount++;
        idDetailRow = rowCount++;
        disableAvatarRow = rowCount++;
        disableAvatarDetailRow = rowCount++;
        renameChatRow = rowCount++;
        renameChatDetailRow = rowCount++;
        deleteMyMessagesRow = rowCount++;
        deleteMyMessagesDetailRow = rowCount++;
        deleteAfterReadRow = rowCount++;
        deleteAfterReadDetailRow = rowCount++;
        savedChannelsRow = rowCount++;
        savedChannelsDetailRow = rowCount++;
        reactionsRow = rowCount++;
        reactionsDetailRow = rowCount++;
        foreignAgentsRow = rowCount++;
        foreignAgentsDetailRow = rowCount++;
        onScreenLockActionRow = rowCount++;
        onScreenLockActionDetailRow = rowCount++;
        showUpdatesRow = rowCount++;
        showUpdatesDetailRow = rowCount++;
        showCallButtonRow = rowCount++;
        showCallButtonDetailRow = rowCount++;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != versionDetailRow && position != idDetailRow && position != disableAvatarDetailRow
                    && position != renameChatDetailRow && position != deleteMyMessagesDetailRow && position != deleteAfterReadDetailRow
                    && position != savedChannelsDetailRow && position != reactionsDetailRow && position != foreignAgentsDetailRow
                    && position != onScreenLockActionDetailRow && position != showUpdatesDetailRow && position != showCallButtonDetailRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == versionRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ShowVersion", R.string.ShowVersion),
                                SharedConfig.showVersion, true);
                    } else if (position == idRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ShowId", R.string.ShowId),
                                SharedConfig.showId, true);
                    } else if (position == disableAvatarRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AvatarDisabling", R.string.AvatarDisabling),
                                SharedConfig.allowDisableAvatar, true);
                    } else if (position == renameChatRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ChatRenaming", R.string.ChatRenaming),
                                SharedConfig.allowRenameChat, true);
                    } else if (position == deleteMyMessagesRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DeletingMyMessages", R.string.DeletingMyMessages),
                                SharedConfig.showDeleteMyMessages, true);
                    } else if (position == deleteAfterReadRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DeletingAfterRead", R.string.DeletingAfterRead),
                                SharedConfig.showDeleteAfterRead, false);
                    } else if (position == savedChannelsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("SavedChannelsSetting", R.string.SavedChannelsSetting),
                                SharedConfig.showSavedChannels, false);
                    } else if (position == reactionsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ReactToMessages", R.string.ReactToMessages),
                                SharedConfig.allowReactions, false);
                    } else if (position == foreignAgentsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("CutForeignAgentsText", R.string.CutForeignAgentsText),
                                SharedConfig.cutForeignAgentsText, false);
                    }  else if (position == showUpdatesRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ShowUpdates", R.string.ShowUpdates),
                                SharedConfig.showUpdates, false);
                    }  else if (position == showCallButtonRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ShowCallButton", R.string.ShowCallButton),
                                SharedConfig.showCallButton, false);
                    }
                    break;
                }
                case 1: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == versionDetailRow) {
                        cell.setText(LocaleController.getString("ShowVersionInfo", R.string.ShowVersionInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == idDetailRow) {
                        cell.setText(LocaleController.getString("ShowIdInfo", R.string.ShowIdInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == disableAvatarDetailRow) {
                        cell.setText(LocaleController.getString("AvatarDisablingInfo", R.string.AvatarDisablingInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == renameChatDetailRow) {
                        cell.setText(LocaleController.getString("ChatRenamingInfo", R.string.ChatRenamingInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == deleteMyMessagesDetailRow) {
                        cell.setText(LocaleController.getString("DeletingMyMessagesInfo", R.string.DeletingMyMessagesInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == deleteAfterReadDetailRow) {
                        cell.setText(LocaleController.getString("DeletingAfterReadInfo", R.string.DeletingAfterReadInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == savedChannelsDetailRow) {
                        cell.setText(LocaleController.getString("SavedChannelsSettingInfo", R.string.SavedChannelsSettingInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == reactionsDetailRow) {
                        cell.setText(LocaleController.getString("ReactToMessagesInfo", R.string.ReactToMessagesInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == foreignAgentsDetailRow) {
                        cell.setText(LocaleController.getString("CutForeignAgentsTextInfo", R.string.CutForeignAgentsTextInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == onScreenLockActionDetailRow) {
                        cell.setText(LocaleController.getString("OnScreenLockActionInfo", R.string.OnScreenLockActionInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == showUpdatesDetailRow) {
                        cell.setText(LocaleController.getString("ShowUpdatesInfo", R.string.ShowUpdatesInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == showCallButtonDetailRow) {
                        cell.setText(LocaleController.getString("ShowCallButtonInfo", R.string.ShowCallButtonInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 2: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    String value = null;
                    if (position == onScreenLockActionRow) {
                        switch (SharedConfig.onScreenLockAction) {
                            case 0:
                                value = LocaleController.getString("OnScreenLockActionNothing", R.string.OnScreenLockActionNothing);
                                break;
                            case 1:
                                value = LocaleController.getString("OnScreenLockActionHide", R.string.OnScreenLockActionHide);
                                break;
                            case 2:
                                value = LocaleController.getString("OnScreenLockActionClose", R.string.OnScreenLockActionClose);
                                break;
                        }
                        textCell.setTextAndValue(LocaleController.getString("OnScreenLockActionTitle", R.string.OnScreenLockActionTitle), value, true);
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == versionRow || position == idRow || position == disableAvatarRow
                    || position == renameChatRow || position == deleteMyMessagesRow || position == deleteAfterReadRow
                    || position == savedChannelsRow || position == reactionsRow || position == foreignAgentsRow
                    || position == showUpdatesRow || position == showCallButtonRow) {
                return 0;
            } else if (position == versionDetailRow || position == idDetailRow || position == disableAvatarDetailRow
                    || position == renameChatDetailRow || position == deleteMyMessagesDetailRow || position == deleteAfterReadDetailRow
                    || position == savedChannelsDetailRow || position == reactionsDetailRow || position == foreignAgentsDetailRow
                    || position == onScreenLockActionDetailRow || position == showUpdatesDetailRow || position == showCallButtonDetailRow) {
                return 1;
            } else if (position == onScreenLockActionRow) {
                return 2;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }
}
