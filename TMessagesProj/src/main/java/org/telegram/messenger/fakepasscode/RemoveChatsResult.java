package org.telegram.messenger.fakepasscode;

import java.util.ArrayList;

public class RemoveChatsResult implements ChatFilter {
    ArrayList<Long> removeNewMessagesChats = new ArrayList<>();
    ArrayList<Long> removedChats = new ArrayList<>();
    @Deprecated
    ArrayList<Long> hiddenChats = new ArrayList<>();
    ArrayList<RemoveChatsAction.HiddenChatEntry> hiddenChatEntries = new ArrayList<>();
    ArrayList<Integer> hiddenFolders = new ArrayList<>();

    @Override
    public boolean isRemoveNewMessagesFromChat(long chatId) {
        if (removeNewMessagesChats == null || removeNewMessagesChats.isEmpty()) {
            return false;
        }
        return removeNewMessagesChats.contains(chatId);
    }

    @Override
    public boolean isHideChat(long chatId, boolean strictHiding) {
        if (hiddenChatEntries != null && hiddenChatEntries.stream().anyMatch(entry -> entry.isHideChat(chatId, strictHiding))) {
            return true;
        } else if (removedChats != null && (removedChats.contains(chatId) || removedChats.contains(-chatId))) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isRemovedChat(long chatId) {
        return removedChats != null && (removedChats.contains(chatId) || removedChats.contains(-chatId));
    }

    @Override
    public boolean isHideFolder(int folderId) {
        if (hiddenFolders == null || hiddenFolders.isEmpty()) {
            return false;
        }
        return hiddenFolders.contains(folderId);
    }

    public boolean hasRemovedChats() {
        return !removedChats.isEmpty();
    }

    public boolean hasHiddenChats() {
        return !hiddenChatEntries.isEmpty();
    }

    public void migrate() {
        for (Long hiddenChatId : hiddenChats) {
            hiddenChatEntries.add(new RemoveChatsAction.HiddenChatEntry(hiddenChatId, false));
        }
        hiddenChats.clear();
    }
}
