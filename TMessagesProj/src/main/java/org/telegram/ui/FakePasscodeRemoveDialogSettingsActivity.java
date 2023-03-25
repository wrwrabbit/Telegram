package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxThreeStateCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SimpleRadioButtonCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CheckBoxSquareThreeState;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FakePasscodeRemoveDialogSettingsActivity extends BaseFragment {
    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private RemoveChatsAction action;
    int accountNum;
    Collection<Long> dialogIds;
    private List<RemoveChatsAction.RemoveChatEntry> entries = new ArrayList<>();
    private boolean isNew;
    private boolean changed;

    private int rowCount;

    private int deleteDialogRow;
    private int deleteDialogDetailsEmptyRow;
    private int deleteFromCompanionRow;
    private int deleteFromCompanionDetailsRow;
    private int deleteNewMessagesRow;
    private int deleteNewMessagesDetailsRow;
    private int deleteAllMyMessagesRow;
    private int deleteAllMyMessagesDetailsRow;
    private int hideDialogRow;
    private int hideDialogDetailsRow;

    private static final int done_button = 1;

    public FakePasscodeRemoveDialogSettingsActivity(RemoveChatsAction action, Collection<Long> dialogIds, int accountNum) {
        super();
        this.action = action;
        this.dialogIds = new ArrayList<>(dialogIds);
        this.isNew = dialogIds.stream().allMatch(id -> !action.contains(id));
        this.accountNum = accountNum;
    }

    public FakePasscodeRemoveDialogSettingsActivity(RemoveChatsAction action, RemoveChatsAction.RemoveChatEntry entry, int accountNum) {
        super();
        this.action = action;
        this.entries.add(entry);
        this.isNew = false;
        this.accountNum = accountNum;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        initEntries();
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
                    if (isNew || changed) {
                        confirmExit();
                    } else {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString("FakePasscodeRemoveDialogSettingsTitle", R.string.FakePasscodeRemoveDialogSettingsTitle));
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(done_button, LocaleController.getString("Save", R.string.Save).toUpperCase());

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
            if (!view.isEnabled()) {
                return;
            }

            if (position == deleteDialogRow) {
                if (hasHideDialog()) {
                    changed = true;
                }
                for (RemoveChatsAction.RemoveChatEntry entry : entries) {
                    entry.isExitFromChat = true;
                }
                updateRows();
                listAdapter.notifyDataSetChanged();
            } else if (position == deleteFromCompanionRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : entries) {
                    entry.isDeleteFromCompanion = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            } else if (position == deleteNewMessagesRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : entries) {
                    entry.isDeleteNewMessages = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            } else if (position == deleteAllMyMessagesRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : entries) {
                    entry.isClearChat = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            } else if (position == hideDialogRow) {
                if (SharedConfig.showHideDialogIsNotSafeWarning) {
                    showHideDialogIsNotSafeWarning();
                }

                if (hasDeleteDialog()) {
                    changed = true;
                }
                for (RemoveChatsAction.RemoveChatEntry entry : entries) {
                    entry.isExitFromChat = false;
                    entry.isClearChat = false;
                    entry.isDeleteFromCompanion = false;
                    entry.isDeleteNewMessages = false;
                }
                updateRows();
                listAdapter.notifyDataSetChanged();
            }
        });

        return fragmentView;
    }

    private void showHideDialogIsNotSafeWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("Warning", R.string.Warning));
        builder.setMessage(LocaleController.getString("HideDialogIsNotSafeWarningMessage", R.string.HideDialogIsNotSafeWarningMessage));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setNegativeButton(LocaleController.getString("DoNotShowAgain", R.string.DoNotShowAgain), (dialog, whichButton) -> {
            SharedConfig.showHideDialogIsNotSafeWarning = false;
            SharedConfig.saveConfig();
        });
        showDialog(builder.create());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (isNew || changed) {
            confirmExit();
        } else {
            finishFragment();
        }
        return false;
    }

    private void updateRows() {
        rowCount = 0;

        deleteDialogRow = -1;
        deleteFromCompanionRow = -1;
        deleteFromCompanionDetailsRow = -1;
        deleteNewMessagesRow = -1;
        deleteNewMessagesDetailsRow = -1;
        deleteAllMyMessagesRow = -1;
        deleteAllMyMessagesDetailsRow = -1;
        deleteDialogDetailsEmptyRow = -1;
        hideDialogRow = -1;
        hideDialogDetailsRow = -1;

        deleteDialogRow = rowCount++;

        if (hasUsers() || hasEncryptedChats()) {
            deleteFromCompanionRow = rowCount++;
            deleteFromCompanionDetailsRow = rowCount++;
        }

        if (hasUsers()) {
            deleteNewMessagesRow = rowCount++;
            deleteNewMessagesDetailsRow = rowCount++;
        }

        if (hasChats()) {
            deleteAllMyMessagesRow = rowCount++;
            deleteAllMyMessagesDetailsRow = rowCount++;
        }

        if (deleteDialogRow == rowCount - 1) {
            deleteDialogDetailsEmptyRow = rowCount++;
        }

        if (!hasOnlySavedMessages()) {
            hideDialogRow = rowCount++;
            hideDialogDetailsRow = rowCount++;
        }
    }

    private void initEntries() {
        if (dialogIds == null) {
            return;
        }

        for (Long id : dialogIds) {
            if (action.contains(id)) {
                entries.add(action.get(id).copy());
            } else {
                String title = getUserConfig().getChatTitleOverride(id);
                if (title == null) {
                    if (DialogObject.isUserDialog(id)) {
                        title = UserObject.getUserName(getMessagesController().getUser(id), getCurrentAccount());
                    } else if (DialogObject.isEncryptedDialog(id)) {
                        TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(id));
                        TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                        title = UserObject.getUserName(user, getCurrentAccount());
                    } else if (DialogObject.isChatDialog(id)) {
                        title = getMessagesController().getChat(-id).title;
                    } else {
                        title = "";
                    }
                }
                entries.add(new RemoveChatsAction.RemoveChatEntry(id, title));
            }
        }
    }

    private void processDone() {
        if (hasIndeterminateStates()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            String buttonText;
            builder.setMessage(LocaleController.getString("RemoveDialogCantSaveDetails", R.string.RemoveDialogCantSaveDetails));
            builder.setTitle(LocaleController.getString("RemoveDialogCantSaveTitle", R.string.RemoveDialogCantSaveTitle));
            buttonText = LocaleController.getString("OK", R.string.OK);
            builder.setPositiveButton(buttonText, null);
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
        } else {
            for (RemoveChatsAction.RemoveChatEntry entry : entries) {
                action.remove(entry.chatId);
                action.add(entry);
                SharedConfig.saveConfig();
            }
            finishFragment();
        }
    }

    private boolean hasUsers() {
        return entries.stream().map(e -> (long)e.chatId).anyMatch(DialogObject::isUserDialog);
    }

    private boolean hasEncryptedChats() {
        return entries.stream().map(e -> (long)e.chatId).anyMatch(DialogObject::isEncryptedDialog);
    }

    private boolean hasChats() {
        return entries.stream().map(e -> (long)e.chatId).anyMatch(did -> {
            if (!DialogObject.isChatDialog(did)) {
                return false;
            }
            TLRPC.Chat chat = getMessagesController().getChat(-did);
            return !ChatObject.isChannel(chat) || chat.megagroup;
        });
    }

    private boolean hasSavedMessages() {
        return entries.stream().anyMatch(e -> e.chatId == getUserConfig().clientUserId);
    }

    private boolean hasOnlySavedMessages() {
        return entries.size() == 1 && entries.get(0).chatId == getUserConfig().clientUserId;
    }

    private boolean hasDeleteDialog() {
        for (RemoveChatsAction.RemoveChatEntry entry : entries) {
            if (entry.isExitFromChat) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHideDialog() {
        for (RemoveChatsAction.RemoveChatEntry entry : entries) {
            if (!entry.isExitFromChat) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIndeterminateStates() {
        if (!hasDeleteDialog()) {
            return false;
        }
        if (hasHideDialog()) {
            return true;
        }
        return getDeleteFromCompanionState() == CheckBoxSquareThreeState.State.INDETERMINATE
                || getDeleteNewMessagesState() == CheckBoxSquareThreeState.State.INDETERMINATE
                || getDeleteAllMyMessagesState() == CheckBoxSquareThreeState.State.INDETERMINATE;
    }

    private void confirmExit() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        String buttonText;
        builder.setMessage(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
        builder.setTitle(LocaleController.getString("PhotoEditorDiscardAlert", R.string.PhotoEditorDiscardAlert));
        buttonText = LocaleController.getString("PassportDiscard", R.string.PassportDiscard);
        builder.setPositiveButton(buttonText, (dialogInterface, i) -> finishFragment());
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        }
    }

    @Override
    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    private CheckBoxSquareThreeState.State getState(Function<RemoveChatsAction.RemoveChatEntry, Boolean> getValue) {
        CheckBoxSquareThreeState.State state = null;
        for (RemoveChatsAction.RemoveChatEntry entry : entries) {
            CheckBoxSquareThreeState.State newState = getValue.apply(entry)
                    ? CheckBoxSquareThreeState.State.CHECKED
                    : CheckBoxSquareThreeState.State.UNCHECKED;
            if (state == null) {
                state = newState;
            } else if (state != newState) {
                return CheckBoxSquareThreeState.State.INDETERMINATE;
            }
        }
        return state;
    }

    private CheckBoxSquareThreeState.State getDeleteFromCompanionState() {
        return getState(e -> e.isDeleteFromCompanion);
    }

    private CheckBoxSquareThreeState.State getDeleteNewMessagesState() {
        return getState(e -> e.isDeleteNewMessages);
    }

    private CheckBoxSquareThreeState.State getDeleteAllMyMessagesState() {
        return getState(e -> e.isClearChat);
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
            if (position == deleteFromCompanionRow || position == deleteNewMessagesRow
                    || position == deleteAllMyMessagesRow) {
                return hasDeleteDialog();
            } else if (position == hideDialogRow) {
                return !hasSavedMessages();
            }
            return true;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new SimpleRadioButtonCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new CheckBoxThreeStateCell(mContext, 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                default:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    SimpleRadioButtonCell radioButtonCell = (SimpleRadioButtonCell) holder.itemView;
                    if (position == deleteDialogRow) {
                        radioButtonCell.setTextAndValue(LocaleController.getString("Delete", R.string.Delete), false, hasDeleteDialog());
                    } else if (position == hideDialogRow) {
                        radioButtonCell.setTextAndValue(LocaleController.getString("Hide", R.string.Hide), false, hasHideDialog());
                    }
                    break;
                }
                case 1: {
                    CheckBoxThreeStateCell checkBoxCell = (CheckBoxThreeStateCell) holder.itemView;
                    if (position == deleteFromCompanionRow) {
                        String title = LocaleController.getString("DeleteFromCompanion", R.string.DeleteFromCompanion);
                        checkBoxCell.setText(title, "", getDeleteFromCompanionState(), false);
                    } else if (position == deleteNewMessagesRow) {
                        String title = LocaleController.getString("DeleteNewMessages", R.string.DeleteNewMessages);
                        checkBoxCell.setText(title, "", getDeleteNewMessagesState(), false);
                    } else if (position == deleteAllMyMessagesRow) {
                        String title = LocaleController.getString("DeleteAllMyMessages", R.string.DeleteAllMyMessages);
                        checkBoxCell.setText(title, "", getDeleteAllMyMessagesState(), false);
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == deleteFromCompanionDetailsRow) {
                        textCell.setText(LocaleController.getString("DeleteFromCompanionDetails", R.string.DeleteFromCompanionDetails));
                    } else if (position == deleteNewMessagesDetailsRow) {
                        textCell.setText(LocaleController.getString("DeleteNewMessagesDetails", R.string.DeleteNewMessagesDetails));
                    } else if (position == deleteAllMyMessagesDetailsRow) {
                        textCell.setText(LocaleController.getString("DeleteAllMyMessagesDetails", R.string.DeleteAllMyMessagesDetails));
                    } else if (position == hideDialogDetailsRow) {
                        textCell.setText(LocaleController.getString("HideDialogDetails", R.string.HideDialogDetails));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == deleteDialogRow || position == hideDialogRow) {
                return 0;
            } else if (position == deleteFromCompanionRow || position == deleteNewMessagesRow
                    || position == deleteAllMyMessagesRow) {
                return 1;
            } else if (position == deleteFromCompanionDetailsRow || position == deleteNewMessagesDetailsRow
                    || position == deleteAllMyMessagesDetailsRow || position == hideDialogDetailsRow) {
                return 2;
            } else if (position == deleteDialogDetailsEmptyRow) {
                return 3;
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
