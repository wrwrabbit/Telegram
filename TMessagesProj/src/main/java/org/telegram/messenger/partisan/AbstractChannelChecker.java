package org.telegram.messenger.partisan;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public abstract class AbstractChannelChecker implements NotificationCenter.NotificationCenterDelegate {
    private final int classGuid;
    protected final int currentAccount;
    private boolean updatesChecked = false;
    private boolean lastMessageLoaded = false;
    private boolean usernameResolved = false;

    protected abstract long getChannelId();
    protected abstract String getChannelUsername();
    protected abstract void processChannelMessages(ArrayList<MessageObject> messages);
    protected abstract void messagesLoadingError();


    protected AbstractChannelChecker(int currentAccount) {
        this.currentAccount = currentAccount;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public void checkUpdate() {
        updatesChecked = false;
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.loadingMessagesFailed);
        getMessagesController().loadMessages(getChannelId(), 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
    }

    public void removeObservers() {
        if (!updatesChecked) {
            getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            processMessagesDidLoad(args);
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            processLoadingMessagesFailed(args);
        }
    }

    private void processMessagesDidLoad(Object[] args) {
        if (FakePasscodeUtils.isFakePasscodeActivated() || (Long) args[0] != getChannelId()) {
            return;
        }
        if (!lastMessageLoaded) {
            lastMessageLoaded(args);
        } else {
            channelMessagesLoaded(args);
        }
    }

    private void lastMessageLoaded(Object[] args) {
        lastMessageLoaded = true;
        getMessagesController().loadMessages(getChannelId(), 0, false, 50, 0, 0, false, 0, classGuid, 2, (int) args[5], 0, 0, 0, 1, false);
    }

    private void channelMessagesLoaded(Object[] args) {
        updatesChecked = true;
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        processChannelMessages((ArrayList<MessageObject>) args[2]);
    }

    private void processLoadingMessagesFailed(Object[] args) {
        if ((int) args[0] != classGuid || args.length < 2 || usernameResolved
                || FakePasscodeUtils.isFakePasscodeActivated()) {
            return;
        }
        TLRPC.InputPeer peer = getPeerFromRequest(args[1]);
        if (peer != null) {
            long channelId = peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
            if (Math.abs(channelId) == Math.abs(getChannelId())) {
                resolveUsername();
            }
        }
    }

    private TLRPC.InputPeer getPeerFromRequest(Object req) {
        if (!(req instanceof TLRPC.TL_messages_getPeerDialogs)) {
            return null;
        }
        TLRPC.TL_messages_getPeerDialogs castedReq = (TLRPC.TL_messages_getPeerDialogs) req;
        if (castedReq.peers.isEmpty()) {
            return null;
        }
        TLRPC.InputDialogPeer inputDialogPeer = castedReq.peers.get(0);
        if (!(inputDialogPeer instanceof TLRPC.TL_inputDialogPeer)) {
            return null;
        }
        return ((TLRPC.TL_inputDialogPeer)inputDialogPeer).peer;
    }

    private void resolveUsername() {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = getChannelUsername();
        getConnectionsManager().sendRequest(req, this::usernameResolvingResponseReceived);
    }

    private void usernameResolvingResponseReceived(TLObject response, TLRPC.TL_error error) {
        usernameResolved = true;
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
            if (response != null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                putUsersAndChats(res);
                getMessagesController().loadMessages(getChannelId(), 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
            } else {
                getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
                messagesLoadingError();
            }
        });
    }

    private void putUsersAndChats(TLRPC.TL_contacts_resolvedPeer res) {
        getMessagesController().putUsers(res.users, false);
        getMessagesController().putChats(res.chats, false);
        getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
    }

    protected AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    private NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    private MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    private MessagesStorage getMessagesStorage() {
        return getAccountInstance().getMessagesStorage();
    }

    protected ConnectionsManager getConnectionsManager() {
        return getAccountInstance().getConnectionsManager();
    }
}
