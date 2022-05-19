/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.graphics.drawable.DrawableCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class ChatRemoveCell extends FrameLayout {

    private final BackupImageView avatarImageView;
    private final LinearLayout nameLayout;
    private final ImageView lockImageView;
    private final SimpleTextView nameTextView;
    private final SimpleTextView statusTextView;
    private final CheckBox2 checkBox;
    private final AvatarDrawable avatarDrawable;
    private Object currentObject;
    private CharSequence currentName;
    private CharSequence currentStatus;
    private final ImageView settingsButton;

    private final int currentAccount;

    private String lastName;
    private int lastStatus;

    Runnable onSettingsClick;

    public ChatRemoveCell(Context context, int account) {
        super(context);

        currentAccount = account;

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 13, 6, LocaleController.isRTL ? 13 : 0, 0));

        nameLayout = new LinearLayout(context);
        nameLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(nameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 72, 10, 60, 0));

        lockImageView = new ImageView(context);
        lockImageView.setImageDrawable(Theme.dialogs_lockDrawable);
        nameLayout.addView(lockImageView, AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        nameLayout.addView(nameTextView);

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 72, 32, 60, 0));

        settingsButton = new ImageView(getContext());
        settingsButton.setScaleType(ImageView.ScaleType.CENTER);
        settingsButton.setImageResource(R.drawable.menu_settings);
        Drawable drawable = DrawableCompat.wrap(settingsButton.getDrawable());
        DrawableCompat.setTintList(drawable, new ColorStateList(new int[][]{
                {}
        }, new int[] {
                Theme.getColor(Theme.key_dialogFloatingButton),
        }));
        settingsButton.setImageDrawable(drawable);
        settingsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector)));
        settingsButton.setPadding(AndroidUtilities.dp(1), 0, 0, 0);
        addView(settingsButton, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 13 : 0, 6, LocaleController.isRTL ? 0 : 13, 0));
        settingsButton.setOnClickListener(v -> {
            if (onSettingsClick != null) {
                onSettingsClick.run();
            }
        });

        checkBox = new CheckBox2(context, 21);
        checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 33, LocaleController.isRTL ? 39 : 0, 0));

        setWillNotDraw(false);
    }

    public void setObject(Object object, CharSequence name, CharSequence status) {
        currentObject = object;
        currentStatus = status;
        currentName = name;
        update(0);
    }

    public void setChecked(boolean checked, boolean animated) {
        settingsButton.setVisibility(checked ? VISIBLE : GONE);
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        }
    }

    public void setCheckBoxEnabled(boolean enabled) {
        if (checkBox != null) {
            checkBox.setEnabled(enabled);
        }
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public long getDialogId() {
        if (currentObject instanceof TLRPC.User) {
            return ((TLRPC.User) currentObject).id;
        } else if (currentObject instanceof TLRPC.EncryptedChat) {
            return DialogObject.makeEncryptedDialogId(((TLRPC.EncryptedChat) currentObject).id);
        } else if (currentObject instanceof TLRPC.Chat) {
            return -((TLRPC.Chat) currentObject).id;
        } else if (currentObject instanceof RemoveChatsAction.RemoveChatEntry) {
            return ((RemoveChatsAction.RemoveChatEntry)currentObject).chatId;
        } else {
            return 0;
        }
    }

    public void setOnSettingsClick(Runnable onSettingsClick) {
        this.onSettingsClick = onSettingsClick;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY));
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update(int mask) {
        if (currentStatus != null && TextUtils.isEmpty(currentStatus)) {
            ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
        } else {
            ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
        }
        avatarImageView.getLayoutParams().width = avatarImageView.getLayoutParams().height = AndroidUtilities.dp(46);
        ((LayoutParams) checkBox.getLayoutParams()).topMargin = AndroidUtilities.dp(33);
        if (LocaleController.isRTL) {
            ((LayoutParams) checkBox.getLayoutParams()).rightMargin = AndroidUtilities.dp(39);
        } else {
            ((LayoutParams) checkBox.getLayoutParams()).leftMargin = AndroidUtilities.dp(40);
        }

        if (currentObject instanceof TLRPC.User) {
            updateUser(mask);
        } else if (currentObject instanceof TLRPC.EncryptedChat) {
            updateEncryptedChat(mask);
        } else if (currentObject instanceof TLRPC.Chat){
            updateChat(mask);
        } else if (currentObject instanceof RemoveChatsAction.RemoveChatEntry) {
            updateRemoveChatEntry(mask);
        }

        if (DialogObject.isEncryptedDialog(getDialogId())) {
            lockImageView.setVisibility(VISIBLE);
            nameTextView.setTextColor(Theme.getColor(Theme.key_chats_secretName));
            nameTextView.setPadding(AndroidUtilities.dp(3), 0, 0, 0);
        } else {
            lockImageView.setVisibility(GONE);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setPadding(0, 0, 0, 0);

        }

        if (currentStatus != null) {
            statusTextView.setText(currentStatus, true);
            statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }

    private void updateUser(int mask) {
        TLRPC.FileLocation photo = null;
        String newName = null;

        TLRPC.User currentUser = (TLRPC.User) currentObject;
        if (UserObject.isUserSelf(currentUser)) {
            nameTextView.setText(LocaleController.getString("SavedMessages", R.string.SavedMessages), true);
            statusTextView.setText(null);
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser);
            ((LayoutParams) nameLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
            return;
        }
        if (currentUser.photo != null) {
            photo = currentUser.photo.photo_small;
        }
        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (photo != null) {
                    continueUpdate = true;
                }
            }
            if (currentUser != null && currentStatus == null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                int newStatus = 0;
                if (currentUser.status != null) {
                    newStatus = currentUser.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                newName = UserObject.getUserName(currentUser, currentAccount);
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate) {
                return;
            }
        }
        avatarDrawable.setInfo(currentUser, currentAccount);
        lastStatus = currentUser.status != null ? currentUser.status.expires : 0;

        if (currentName != null) {
            lastName = null;
            nameTextView.setText(currentName, true);
        } else {
            lastName = newName == null ? UserObject.getUserName(currentUser, currentAccount) : newName;
            nameTextView.setText(lastName);
        }

        if (currentStatus == null) {
            if (currentUser.bot) {
                statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                statusTextView.setText(LocaleController.getString("Bot", R.string.Bot));
            } else {
                if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || getMessagesController().onlinePrivacy.containsKey(currentUser.id)) {
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteBlueText);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    statusTextView.setText(LocaleController.getString("Online", R.string.Online));
                } else {
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser));
                }
            }
        } else if (currentStatus.toString().equals("")) {
            if (getMessagesController().getAllDialogs().stream().noneMatch(d -> d.id == getDialogId())){
                currentStatus = LocaleController.getString("ChatRemoved", R.string.ChatRemoved);
            }
        }

        avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
    }

    private void updateEncryptedChat(int mask) {
        TLRPC.FileLocation photo = null;
        String newName = null;

        TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) currentObject;
        TLRPC.User currentUser = getMessagesController().getUser(encryptedChat.user_id);
        if (currentUser == null) {
            return;
        }
        if (currentUser.photo != null) {
            photo = currentUser.photo.photo_small;
        }
        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (photo != null) {
                    continueUpdate = true;
                }
            }
            if (currentUser != null && currentStatus == null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                int newStatus = 0;
                if (currentUser.status != null) {
                    newStatus = currentUser.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                newName = UserObject.getUserName(currentUser, currentAccount);
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate) {
                return;
            }
        }
        avatarDrawable.setInfo(currentUser, currentAccount);
        lastStatus = currentUser.status != null ? currentUser.status.expires : 0;

        if (currentName != null) {
            lastName = null;
            nameTextView.setText(currentName, true);
        } else {
            lastName = newName == null ? UserObject.getUserName(currentUser, currentAccount) : newName;
            nameTextView.setText(lastName);
        }

        if (currentStatus == null) {
            if (currentUser.bot) {
                statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                statusTextView.setText(LocaleController.getString("Bot", R.string.Bot));
            } else {
                if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || getMessagesController().onlinePrivacy.containsKey(currentUser.id)) {
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteBlueText);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    statusTextView.setText(LocaleController.getString("Online", R.string.Online));
                } else {
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser));
                }
            }
        } else if (currentStatus.toString().equals("")) {
            if (getMessagesController().getAllDialogs().stream().noneMatch(d -> d.id == getDialogId())){
                currentStatus = LocaleController.getString("ChatRemoved", R.string.ChatRemoved);
            }
        }

        avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
    }

    private void updateChat(int mask) {
        TLRPC.FileLocation photo = null;
        String newName = null;

        TLRPC.Chat currentChat = (TLRPC.Chat) currentObject;
        if (currentChat.photo != null) {
            photo = currentChat.photo.photo_small;
        }
        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (photo != null) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                newName = UserConfig.getChatTitleOverride(currentAccount, currentChat.id);
                if (newName == null) {
                    newName = currentChat.title;
                }
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate) {
                return;
            }
        }

        avatarDrawable.setInfo(currentChat, currentAccount);

        if (currentName != null) {
            lastName = null;
            nameTextView.setText(currentName, true);
        } else {
            String title = UserConfig.getChatTitleOverride(currentAccount, currentChat.id);
            if (title == null) {
                title = currentChat.title;
            }
            lastName = newName == null ? title : newName;
            nameTextView.setText(lastName);
        }

        if (currentStatus == null) {
            statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            statusTextView.setText(getChatStatus(currentChat));
        } else if (currentStatus.toString().equals("") && currentChat.left) {
            currentStatus = LocaleController.getString("ChatRemoved", R.string.ChatRemoved);
        }

        avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
    }

    private void updateRemoveChatEntry(int mask) {
        TLRPC.FileLocation photo = null;
        String newName = null;

        RemoveChatsAction.RemoveChatEntry entry = (RemoveChatsAction.RemoveChatEntry) currentObject;
        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (photo != null) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                newName = UserConfig.getChatTitleOverride(currentAccount, entry.chatId);
                if (newName == null) {
                    newName = entry.title;
                }
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate) {
                return;
            }
        }

        avatarDrawable.setInfo(entry.chatId, entry.title, "");

        if (currentName != null) {
            lastName = null;
            nameTextView.setText(currentName, true);
        } else {
            lastName = newName == null ? entry.title : newName;
            nameTextView.setText(lastName);
        }

        if (currentStatus == null) {
            statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            statusTextView.setText(LocaleController.getString("ChatRemoved", R.string.ChatRemoved));
        } else if (currentStatus.toString().equals("")) {
            currentStatus = LocaleController.getString("ChatRemoved", R.string.ChatRemoved);
        }

        avatarImageView.setForUserOrChat(null, avatarDrawable);
    }

    private String getChatStatus(TLRPC.Chat currentChat) {
        if (currentChat.participants_count != 0) {
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                return LocaleController.formatPluralString("Subscribers", currentChat.participants_count);
            } else {
                return LocaleController.formatPluralString("Members", currentChat.participants_count);
            }
        } else if (currentChat.has_geo) {
            return LocaleController.getString("MegaLocation", R.string.MegaLocation);
        } else if (TextUtils.isEmpty(currentChat.username)) {
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                return LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate);
            } else {
                return LocaleController.getString("MegaPrivate", R.string.MegaPrivate);
            }
        } else {
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                return LocaleController.getString("ChannelPublic", R.string.ChannelPublic);
            } else {
                return LocaleController.getString("MegaPublic", R.string.MegaPublic);
            }
        }
    }

    public String getName() {
        return currentName != null ? currentName.toString() : lastName;
    }

    public void setSelected(boolean selected) {
        if (selected) {
            setBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector));
        } else {
            setBackground(null);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(currentAccount);
    }
}