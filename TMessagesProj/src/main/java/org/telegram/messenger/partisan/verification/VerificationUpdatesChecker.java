package org.telegram.messenger.partisan.verification;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VerificationUpdatesChecker implements NotificationCenter.NotificationCenterDelegate {
    private final Set<VerificationStorage> storageLastMessageLoaded = new HashSet<>();
    private final Set<VerificationStorage> verificationUpdatesChecked = new HashSet<>();
    private final Set<String> resolvedUsernames = new HashSet<>();
    private final int currentAccount;
    private final boolean force;
    private final int classGuid;
    private final int checkDelay = 6 * 60 * 60;

    public VerificationUpdatesChecker(int currentAccount, boolean force) {
        this.currentAccount = currentAccount;
        this.force = force;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public static void checkUpdate(int currentAccount, boolean force) {
        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            VerificationUpdatesChecker checker = new VerificationUpdatesChecker(currentAccount, force);
            checker.checkUpdate();
        }
    }

    public void checkUpdate() {
        Utilities.globalQueue.postRunnable(() -> {
            boolean observersAdded = false;
            for (VerificationStorage storage : VerificationRepository.getInstance().getStorages()) {
                if (!force && Math.abs(System.currentTimeMillis() - storage.lastCheckTime) < checkDelay * 1000) {
                    continue;
                }
                if (!observersAdded) {
                    getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
                    getNotificationCenter().addObserver(this, NotificationCenter.loadingMessagesFailed);
                    observersAdded = true;
                }
                VerificationRepository.getInstance().saveLastCheckTime(storage.chatId, System.currentTimeMillis());
                getMessagesController().loadMessages(storage.chatId, 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
            }
        });
    }

    public void removeObservers() {
        if (!isAllStoragesChecked()) {
            getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        }
    }

    private boolean isAllStoragesChecked() {
        return verificationUpdatesChecked.containsAll(VerificationRepository.getInstance().getStorages());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            processMessagesDidLoad(args);
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            processLoadingMessagesFailed(args);
        }
    }

    private void processMessagesDidLoad(Object... args) {
        VerificationStorage storage = VerificationRepository.getInstance().getStorage((Long)args[0]);
        if (storage != null) {
            synchronized (resolvedUsernames) {
                resolvedUsernames.add(storage.chatUsername);
            }
            int lastMessageId = Math.max((int)args[5], storage.lastCheckedMessageId);
            VerificationRepository.getInstance().saveLastCheckedMessageId(storage.chatId, lastMessageId);
            if (!storageLastMessageLoaded.contains(storage)) {
                lastMessageLoaded(storage, lastMessageId);
            } else {
                channelMessagesLoaded(storage, (ArrayList<MessageObject>)args[2], lastMessageId);
            }
        }
    }

    void lastMessageLoaded(VerificationStorage storage, int lastMessageId) {
        storageLastMessageLoaded.add(storage);
        getMessagesController().loadMessages(storage.chatId, 0, false, 50, lastMessageId, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
    }

    void channelMessagesLoaded(VerificationStorage storage, List<MessageObject> messages, int lastMessageId) {
        processChannelMessages(storage.chatId, messages);
        boolean isEnd = messages.size() < 50;
        if (isEnd) {
            verificationUpdatesChecked.add(storage);
            removeObservers();
        } else {
            getMessagesController().loadMessages(storage.chatId, 0, false, 50, lastMessageId, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
        }
    }

    private void processLoadingMessagesFailed(Object... args) {
        if (args.length > 1 && args[1] instanceof TLRPC.TL_messages_getPeerDialogs) {
            TLRPC.TL_messages_getPeerDialogs oldReq = (TLRPC.TL_messages_getPeerDialogs)args[1];
            if (FakePasscodeUtils.isFakePasscodeActivated()
                    || (int)args[0] != classGuid
                    || oldReq.peers.isEmpty()
                    || !(oldReq.peers.get(0) instanceof TLRPC.TL_inputDialogPeer)) {
                return;
            }
            TLRPC.InputPeer peer = ((TLRPC.TL_inputDialogPeer)oldReq.peers.get(0)).peer;
            VerificationStorage storage = VerificationRepository.getInstance().getStorage(peer);
            if (storage != null) {
                synchronized (resolvedUsernames) {
                    if (!resolvedUsernames.contains(storage.chatUsername)) {
                        resolveStorageUsername(storage);
                    }
                }
            }
        }
    }

    void resolveStorageUsername(VerificationStorage storage) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = storage.chatUsername;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) ->
                storageUsernameResolvingResponseReceived(storage, response, error));
    }

    void storageUsernameResolvingResponseReceived(VerificationStorage storage, TLObject response, TLRPC.TL_error error) {
        synchronized (resolvedUsernames) {
            resolvedUsernames.add(storage.chatUsername);
        }
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
            if (response != null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                putUsersAndChats(res);
                long chatId = peerToChatId(res.peer);
                VerificationRepository.getInstance().saveRepositoryChatId(storage.chatUsername, -chatId);
                getMessagesController().loadMessages(-chatId, 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
            } else {
                getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
            }
        });
    }

    private void putUsersAndChats(TLRPC.TL_contacts_resolvedPeer response) {
        MessagesController.getInstance(currentAccount).putUsers(response.users, false);
        MessagesController.getInstance(currentAccount).putChats(response.chats, false);
        MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
    }

    private long peerToChatId(TLRPC.Peer peer) {
        return peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
    }

    private void processChannelMessages(long storageChatId, List<MessageObject> messages) {
        List<VerificationChatInfo> chatsToAdd = new ArrayList<>();
        Set<Long> chatsToRemove = new HashSet<>();
        VerificationMessageParser parser = new VerificationMessageParser();
        List<MessageObject> sortedMessages = messages.stream()
                .sorted(Comparator.comparingInt(MessageObject::getId))
                .collect(Collectors.toList());
        for (MessageObject message : sortedMessages) {
            VerificationMessageParser.ParsingResult result = parser.parseMessage(message);
            if (result != null) {
                chatsToAdd.removeIf(c -> result.chatsToRemove.contains(c.chatId));
                chatsToRemove.removeIf(id -> result.chatsToAdd.stream().anyMatch(c -> c.chatId == id));
                chatsToAdd.addAll(result.chatsToAdd);
                chatsToRemove.addAll(result.chatsToRemove);
            }
        }
        VerificationRepository.getInstance().putChats(storageChatId, chatsToAdd);
        VerificationRepository.getInstance().deleteChats(storageChatId, chatsToRemove);
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