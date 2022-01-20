package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RemoveChatsAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {

    public static class RemoveChatEntry {
        public long chatId;
        public boolean isClearChat;
        public boolean isExitFromChat;
        public boolean isDeleteNewMessages;
        public boolean isDeleteFromCompanion;
        public String title;

        public RemoveChatEntry() {}
        public RemoveChatEntry(long chatId, String title) {
            this.chatId = chatId;
            isClearChat = false;
            isExitFromChat = true;
            isDeleteNewMessages = true;
            isDeleteFromCompanion = false;
            this.title = title;
        }

        public RemoveChatEntry copy() {
            RemoveChatEntry copy = new RemoveChatEntry();
            copy.chatId = chatId;
            copy.isClearChat = isClearChat;
            copy.isExitFromChat = isExitFromChat;
            copy.isDeleteNewMessages = isDeleteNewMessages;
            copy.isDeleteFromCompanion = isDeleteFromCompanion;
            copy.title = title;
            return copy;
        }
    }

    @Deprecated
    private ArrayList<Integer> chatsToRemove = new ArrayList<>();
    private List<RemoveChatEntry> chatEntriesToRemove = new ArrayList<>();
    private ArrayList<Long> removedChats = new ArrayList<>(); // Chats to delete new messages
    private ArrayList<Long> realRemovedChats = new ArrayList<>(); // Removed chats
    private ArrayList<Long> hiddenChats = new ArrayList<>();
    private ArrayList<Integer> hiddenFolders = new ArrayList<>();

    private final Set<Long> pendingRemovalChats = new HashSet<>();
    @JsonIgnore
    public static volatile boolean pendingRemovalChatsChecked = false;

    public RemoveChatsAction() {}

    public RemoveChatsAction(int accountNum, ArrayList<Integer> chatsToRemove) {
        this.accountNum = accountNum;
        this.chatsToRemove = chatsToRemove;
    }

    void clear() {
        chatsToRemove = new ArrayList<>();
        chatEntriesToRemove = new ArrayList<>();
        SharedConfig.saveConfig();
    }

    public List<RemoveChatEntry> getChatEntriesToRemove() {
        return chatEntriesToRemove;
    }

    public boolean isRemoveNewMessagesFromChat(long chatId) {
        if (removedChats == null || removedChats.isEmpty()) {
            return false;
        }
        return removedChats.contains(chatId);
    }

    public boolean isHideChat(long chatId) {
        if (hiddenChats != null && (hiddenChats.contains(chatId) || hiddenChats.contains(-chatId))) {
            return true;
        } else if (pendingRemovalChats.contains(chatId) || pendingRemovalChats.contains(-chatId)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isRemovedChat(long chatId) {
        return realRemovedChats != null && (realRemovedChats.contains(chatId) || realRemovedChats.contains(-chatId));
    }

    public boolean isHideFolder(int folderId) {
        if (hiddenFolders == null || hiddenFolders.isEmpty()) {
            return false;
        }
        return hiddenFolders.contains(folderId);
    }

    public boolean contains(long chatId) {
        return chatEntriesToRemove.stream().anyMatch(e -> e.chatId == chatId);
    }

    public void add(long chatId, String title) {
        RemoveChatEntry entry = new RemoveChatEntry(chatId, title);
        add(entry);
    }

    public void add(RemoveChatEntry entry) {
        chatEntriesToRemove.add(entry);
    }

    public void remove(long chatId) {
        chatEntriesToRemove.removeIf(e -> e.chatId == chatId);
    }

    public RemoveChatEntry get(long chatId) {
        return chatEntriesToRemove.stream().filter(e -> e.chatId == chatId).findAny().orElse(null);
    }

    public Set<Long> getIds() {
        return chatEntriesToRemove.stream().map(e -> e.chatId).collect(Collectors.toSet());
    }

    public void execute() {
        synchronized (RemoveChatsAction.class) {
            pendingRemovalChatsChecked = true;
        }
        NotificationCenter notificationCenter = NotificationCenter.getInstance(accountNum);
        removedChats.clear();
        realRemovedChats.clear();
        hiddenChats.clear();
        hiddenFolders.clear();
        synchronized (pendingRemovalChats) {
            pendingRemovalChats.clear();
        }
        if (chatEntriesToRemove.isEmpty()) {
            SharedConfig.saveConfig();
            notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload);
            return;
        }
        clearFolders();
        for (RemoveChatEntry entry : chatEntriesToRemove) {
            if (entry.isClearChat && Utils.isNetworkConnected() && isChat(entry.chatId)) {
                if (entry.isExitFromChat) {
                    synchronized (pendingRemovalChats) {
                        if (pendingRemovalChats.isEmpty()) {
                            notificationCenter.addObserver(this, NotificationCenter.dialogCleared);
                        }
                        pendingRemovalChats.add(entry.chatId);
                    }
                }
                getMessagesController().deleteAllMessagesFromDialog(entry.chatId, getUserConfig().clientUserId);
            } else if (entry.isExitFromChat) {
                Utils.deleteDialog(accountNum, entry.chatId, entry.isDeleteFromCompanion);
                notificationCenter.postNotificationName(NotificationCenter.dialogDeletedByAction, entry.chatId);
            }
        }
        removedChats = chatEntriesToRemove.stream().filter(e -> e.isExitFromChat && e.isDeleteNewMessages).map(e -> e.chatId).collect(Collectors.toCollection(ArrayList::new));
        realRemovedChats = chatEntriesToRemove.stream().filter(e -> e.isExitFromChat).map(e -> e.chatId).collect(Collectors.toCollection(ArrayList::new));
        hiddenChats = chatEntriesToRemove.stream().filter(e -> !e.isExitFromChat).map(e -> e.chatId).collect(Collectors.toCollection(ArrayList::new));
        if (!hiddenChats.isEmpty()) {
            notificationCenter.postNotificationName(NotificationCenter.dialogHiddenByAction);
        }
        if (!hiddenFolders.isEmpty()) {
            notificationCenter.postNotificationName(NotificationCenter.foldersHiddenByAction);
        }
        SharedConfig.saveConfig();
        getMessagesStorage().removeChatsActionExecuted();
        notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload);
        LongSparseIntArray dialogsToUpdate = new LongSparseIntArray(hiddenChats.size());
        hiddenChats.stream().forEach(c -> dialogsToUpdate.put(c, 0));
        getAccount().getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
    }

    private AccountInstance getAccount() {
        return AccountInstance.getInstance(accountNum);
    }

    private MessagesController getMessagesController() {
        return getAccount().getMessagesController();
    }

    private MessagesStorage getMessagesStorage() {
        return getAccount().getMessagesStorage();
    }

    private void clearFolders() {
        for (MessagesController.DialogFilter folder : getMessagesController().dialogFilters) {
            clearFolder(folder);
        }
    }

    private void clearFolder(MessagesController.DialogFilter folder) {
        if (!folderHasDialogs(folder)) {
            return;
        }

        List<Long> idsToRemove = chatEntriesToRemove.stream().filter(e -> e.isExitFromChat).map(e -> e.chatId).collect(Collectors.toList());
        folder.alwaysShow.removeAll(idsToRemove);
        folder.neverShow.removeAll(idsToRemove);
        for (Long chatId : idsToRemove) {
            if (folder.pinnedDialogs.get(chatId.intValue()) != 0) {
                folder.pinnedDialogs.removeAt(chatId.intValue());
            }
        }
        List<Long> pinnedDialogs = getFolderPinnedDialogs(folder);

        if (folder.alwaysShow.isEmpty() && folder.pinnedDialogs.size() == 0) {
            TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
            req.id = folder.id;
            getMessagesController().removeFilter(folder);
            getMessagesStorage().deleteDialogFilter(folder);
            getAccount().getConnectionsManager().sendRequest(req, (response, error) -> { });
        } else {
            TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
            req.id = folder.id;
            req.flags |= 1;
            req.filter = new TLRPC.TL_dialogFilter();
            req.filter.contacts = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0;
            req.filter.non_contacts = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0;
            req.filter.groups = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0;
            req.filter.broadcasts = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0;
            req.filter.bots = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0;
            req.filter.exclude_muted = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0;
            req.filter.exclude_read = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0;
            req.filter.exclude_archived = (folder.flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0;
            req.filter.id = folder.id;
            req.filter.title = folder.name;
            fillPeerArray(folder.alwaysShow, req.filter.include_peers);
            fillPeerArray(folder.neverShow, req.filter.exclude_peers);
            fillPeerArray(pinnedDialogs, req.filter.pinned_peers);

            Set<Long> idsToHide = chatEntriesToRemove.stream().filter(e -> !e.isExitFromChat).map(e -> e.chatId).collect(Collectors.toSet());
            if (folder.alwaysShow.stream().allMatch(idsToHide::contains)) {
                hiddenFolders.add(folder.id);
            }
        }
    }

    private boolean folderHasDialogs(MessagesController.DialogFilter folder) {
        List<Long> idsToRemove = chatEntriesToRemove.stream().map(e -> e.chatId).collect(Collectors.toList());
        if (!Collections.disjoint(folder.alwaysShow, idsToRemove)) {
            return true;
        }
        if (!Collections.disjoint(folder.neverShow, idsToRemove)) {
            return true;
        }
        if (!Collections.disjoint(getFolderPinnedDialogs(folder), idsToRemove)) {
            return true;
        }
        return false;
    }


    private List<Long> getFolderPinnedDialogs(MessagesController.DialogFilter folder) {
        List<Long> pinnedDialogs = new ArrayList<>();
        for (int a = 0, N = folder.pinnedDialogs.size(); a < N; a++) {
            long key = folder.pinnedDialogs.keyAt(a);
            if (key == 0) {
                continue;
            }
            pinnedDialogs.add(key);
        }
        return pinnedDialogs;
    }

    private void fillPeerArray(List<Long> fromArray, List<TLRPC.InputPeer> toArray) {
        for (int a = 0, N = fromArray.size(); a < N; a++) {
            long did = fromArray.get(a);
            if (did != 0) {
                if (did > 0) {
                    TLRPC.User user = getMessagesController().getUser(did);
                    if (user != null) {
                        TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerUser();
                        inputPeer.user_id = did;
                        inputPeer.access_hash = user.access_hash;
                        toArray.add(inputPeer);
                    }
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-did);
                    if (chat != null) {
                        if (ChatObject.isChannel(chat)) {
                            TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerChannel();
                            inputPeer.channel_id = -did;
                            inputPeer.access_hash = chat.access_hash;
                            toArray.add(inputPeer);
                        } else {
                            TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerChat();
                            inputPeer.chat_id = -did;
                            toArray.add(inputPeer);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void migrate() {
        for (Integer chatId : chatsToRemove) {
            chatEntriesToRemove.add(new RemoveChatEntry((long)chatId, "Unknown"));
        }
        chatsToRemove.clear();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.dialogCleared || account != accountNum || args.length < 1 || !(args[0] instanceof Long)) {
            return;
        }
        deletePendingChat((long)args[0]);
    }

    private boolean isChat(long dialogId)  {
        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        return chat != null && (!ChatObject.isChannel(chat) || chat.megagroup);
    }

    public void checkPendingRemovalChats() {
        synchronized (pendingRemovalChats) {
            List<Long> pendingRemovalChatsCopy = new ArrayList<>(pendingRemovalChats);
            for (long dialogId : pendingRemovalChatsCopy) {
                deletePendingChat(dialogId);
            }
        }
    }

    private void deletePendingChat(long dialogId) {
        FakePasscode fakePasscode = SharedConfig.getActivatedFakePasscode();
        if (fakePasscode == null || fakePasscode.removeChatsActions == null
                || !fakePasscode.removeChatsActions.contains(this)) {
            return;
        }

        NotificationCenter notificationCenter = NotificationCenter.getInstance(accountNum);

        synchronized (pendingRemovalChats) {
            if (!pendingRemovalChats.contains(dialogId)) {
                return;
            }
            pendingRemovalChats.remove(dialogId);
            if (pendingRemovalChats.isEmpty()) {
                notificationCenter.removeObserver(this, NotificationCenter.dialogCleared);
            }
        }

        Utils.deleteDialog(accountNum, dialogId);
        AndroidUtilities.runOnUIThread(() -> Utils.deleteDialog(accountNum, dialogId), 100);
        AndroidUtilities.runOnUIThread(() -> Utils.deleteDialog(accountNum, dialogId), 1000);
        notificationCenter.postNotificationName(NotificationCenter.dialogDeletedByAction, dialogId);
    }
}
