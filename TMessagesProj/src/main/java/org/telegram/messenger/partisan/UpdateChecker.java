package org.telegram.messenger.partisan;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.StickersAlert;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

public class UpdateChecker implements NotificationCenter.NotificationCenterDelegate {
    public interface UpdateCheckedDelegate {
        void onUpdateResult(boolean updateFounded, UpdateData update);
    }

    private final long CYBER_PARTISAN_SECURITY_TG_CHANNEL_ID = BuildVars.isAlphaApp() ? -1716369838 : -1164492294;  // For checking for updates
    private final String CYBER_PARTISAN_SECURITY_TG_CHANNEL_USERNAME = BuildVars.isAlphaApp() ? "ptg_update_test" : "cpartisans_security";

    private boolean partisanTgChannelLastMessageLoaded = false;
    private boolean appUpdatesChecked = false;
    private boolean partisanTgChannelUsernameResolved = false;
    private int currentAccount;
    private UpdateCheckedDelegate delegate;
    private final int classGuid;

    public UpdateChecker(int currentAccount, UpdateCheckedDelegate delegate) {
        this.currentAccount = currentAccount;
        this.delegate = delegate;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    private UpdateChecker() {
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public static void checkUpdate(int currentAccount, UpdateCheckedDelegate delegate) {
        UpdateChecker checker = new UpdateChecker();
        checker.currentAccount = currentAccount;
        checker.delegate = (updateFounded, data) -> {
            checker.removeObservers();
            delegate.onUpdateResult(updateFounded, data);
        };
        checker.checkUpdate();
    }

    public void checkUpdate() {
        appUpdatesChecked = false;
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.loadingMessagesFailed);
        getMessagesController().loadMessages(getUpdateTgChannelId(), 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
    }

    public void removeObservers() {
        if (!appUpdatesChecked) {
            getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            if (SharedConfig.showUpdates && SharedConfig.fakePasscodeActivatedIndex == -1) {
                if ((Long)args[0] == getUpdateTgChannelId()) {
                    if (!partisanTgChannelLastMessageLoaded) {
                        partisanTgChannelLastMessageLoaded = true;
                        getMessagesController().loadMessages(getUpdateTgChannelId(), 0, false, 50, 0, 0, false, 0, classGuid, 2, (int)args[5], 0, 0, 0, 1, false);
                    } else {
                        appUpdatesChecked = true;
                        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
                        getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
                        processPartisanTgChannelMessages((ArrayList<MessageObject>)args[2]);
                    }
                }
            }
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            if (args.length > 1 && args[1] instanceof TLRPC.TL_messages_getPeerDialogs) {
                TLRPC.TL_messages_getPeerDialogs oldReq = (TLRPC.TL_messages_getPeerDialogs)args[1];
                TLRPC.InputPeer peer = null;
                if (!oldReq.peers.isEmpty() && oldReq.peers.get(0) instanceof TLRPC.TL_inputDialogPeer) {
                    peer = ((TLRPC.TL_inputDialogPeer)oldReq.peers.get(0)).peer;
                }
                if (!partisanTgChannelUsernameResolved && SharedConfig.showUpdates && SharedConfig.fakePasscodeActivatedIndex == -1
                        && (int)args[0] == classGuid && peer != null
                        && (peer.channel_id == getUpdateTgChannelId() || peer.chat_id == getUpdateTgChannelId()
                        || peer.channel_id == -getUpdateTgChannelId() || peer.chat_id == -getUpdateTgChannelId())) {
                    TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                    req.username = getUpdateTgChannelUsername();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                        partisanTgChannelUsernameResolved = true;
                        AndroidUtilities.runOnUIThread(() -> {
                            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
                            if (response != null) {
                                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                                getMessagesController().loadMessages(getUpdateTgChannelId(), 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
                            } else {
                                getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
                            }
                        });
                    });
                }
            }
        }
    }

    private void processPartisanTgChannelMessages(ArrayList<MessageObject> messages) {
        UpdateData update = new UpdateData();
        UpdateMessageParser parser = new UpdateMessageParser(currentAccount, getUpdateTgChannelId());
        for (MessageObject message : messages) {
            UpdateData currentUpdate = parser.parseMessage(message);
            if (currentUpdate != null && currentUpdate.version != null &&
                    (update.version == null || currentUpdate.version.greater(update.version))) {
                update = currentUpdate;
            }
        }

        if (update.version != null && (AppVersion.getCurrentVersion() == null || update.version.greater(AppVersion.getCurrentVersion()))) {
            if (update.stickerPackName != null || update.stickerEmoji != null) {
                loadSticker(update);
            } else {
                delegate.onUpdateResult(true, update);
            }
        } else {
            delegate.onUpdateResult(false, null);
        }
    }

    private void loadSticker(UpdateData update) {
        TLRPC.TL_inputStickerSetShortName inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
        inputStickerSet.short_name = update.stickerPackName != null
                ? update.stickerPackName
                : update.stickerEmoji;
        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        TLRPC.TL_messages_stickerSet stickerSet = null;
        if (inputStickerSet.short_name != null) {
            stickerSet = mediaDataController.getStickerSetByName(inputStickerSet.short_name);
        }
        if (stickerSet == null) {
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = inputStickerSet;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_messages_stickerSet loadedStickerSet = (TLRPC.TL_messages_stickerSet) response;
                    update.sticker = getStickerByEmoji(loadedStickerSet, update.stickerEmoji);
                    delegate.onUpdateResult(true, update);
                }
            }));
        } else {
            update.sticker = getStickerByEmoji(stickerSet, update.stickerEmoji);
            delegate.onUpdateResult(true, update);
        }
    }

    private TLRPC.Document getStickerByEmoji(TLRPC.TL_messages_stickerSet stickerSet, String emoji) {
        for (TLRPC.Document document : stickerSet.documents) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    if (!TextUtils.isEmpty(attribute.alt)) {
                        if (attribute.alt.equals(emoji)) {
                            return document;
                        }
                    }
                }
            }
        }
        return null;
    }

    private long getUpdateTgChannelId() {
        if (SharedConfig.updateChannelIdOverride != 0) {
            return SharedConfig.updateChannelIdOverride;
        } else {
            return CYBER_PARTISAN_SECURITY_TG_CHANNEL_ID;
        }
    }

    private String getUpdateTgChannelUsername() {
        if (!Objects.equals(SharedConfig.updateChannelUsernameOverride, "")) {
            return SharedConfig.updateChannelUsernameOverride;
        } else {
            return CYBER_PARTISAN_SECURITY_TG_CHANNEL_USERNAME;
        }
    }

    private AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    private NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    private MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }
}
