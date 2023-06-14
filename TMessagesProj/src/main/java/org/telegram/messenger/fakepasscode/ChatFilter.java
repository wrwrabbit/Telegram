package org.telegram.messenger.fakepasscode;

public interface ChatFilter {
    boolean isRemoveNewMessagesFromChat(long chatId);
    boolean isHideChat(long chatId, boolean strictHiding);
    default boolean isHideChat(long chatId) {
        return isHideChat(chatId, false);
    }
    boolean isHideFolder(int folderId);
}
