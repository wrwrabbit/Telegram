package org.telegram.messenger.fakepasscode.results;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.ChatFilter;

public class HideEncryptedChatsFromEncryptedGroups implements ChatFilter {
    public static HideEncryptedChatsFromEncryptedGroups instance = new HideEncryptedChatsFromEncryptedGroups();

    private HideEncryptedChatsFromEncryptedGroups() {
    }

    @Override
    public boolean isRemoveNewMessagesFromChat(long chatId) {
        return false;
    }

    @Override
    public boolean isHideChat(long chatId, boolean strictHiding) {
        if (DialogObject.isEncryptedDialog(chatId)) {
            int encryptedChatId = DialogObject.getEncryptedChatId(chatId);
            MessagesStorage messagesStorage = MessagesStorage.getInstance(UserConfig.selectedAccount);
            if (messagesStorage.getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isHideFolder(int folderId) {
        return false;
    }
}
