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
import org.telegram.tgnet.ConnectionsManager;
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
    private boolean partisanTgChannelUsernameResolved = false;
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
            VerificationStorage storage = VerificationRepository.getInstance().getStorages().stream()
                    .filter(s -> s.chatId == (Long)args[0])
                    .findAny()
                    .orElse(null);
            if (storage != null) {
                int lastMessageId = Math.max((int)args[5], storage.lastCheckedMessageId);
                VerificationRepository.getInstance().saveLastCheckedMessageId(storage.chatId, lastMessageId);
                if (!storageLastMessageLoaded.contains(storage)) {
                    storageLastMessageLoaded.add(storage);
                    getMessagesController().loadMessages(storage.chatId, 0, false, 50, lastMessageId, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
                } else {
                    ArrayList<MessageObject> messages = (ArrayList<MessageObject>)args[2];
                    processChannelMessages(storage.chatId, messages);

                    boolean isEnd = messages.size() < 50;
                    if (isEnd) {
                        verificationUpdatesChecked.add(storage);
                        removeObservers();
                    } else {
                        getMessagesController().loadMessages(storage.chatId, 0, false, 50, lastMessageId, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
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
                final TLRPC.InputPeer finalPeer = peer;
                if (!partisanTgChannelUsernameResolved && SharedConfig.fakePasscodeActivatedIndex == -1
                        && (int)args[0] == classGuid && peer != null) {
                    VerificationStorage storage = VerificationRepository.getInstance().getStorages().stream()
                            .filter(s -> s.chatId == finalPeer.channel_id
                                || s.chatId == -finalPeer.channel_id
                                || s.chatId == finalPeer.chat_id
                                || s.chatId == -finalPeer.chat_id)
                            .findAny()
                            .orElse(null);
                    if (storage != null) {
                        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                        req.username = storage.chatUsername;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                            partisanTgChannelUsernameResolved = true;
                            AndroidUtilities.runOnUIThread(() -> {
                                getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
                                if (response != null) {
                                    TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                                    getMessagesController().loadMessages(storage.chatId, 0, false, 1, 0, 0, false, 0, classGuid, 2, 0, 0, 0, 0, 1, false);
                                } else {
                                    getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
                                }
                            });
                        });
                    }
                }
            }
        }
    }

    private void processChannelMessages(long storageChatId, ArrayList<MessageObject> messages) {
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