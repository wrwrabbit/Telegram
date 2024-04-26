package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.partisan.PartisanLog;

public class AllMessagesDeleter {
    public interface MessagesDeleterDelegate {
        void onMessagesDeleted();
        void onMessagesDeletedWithErrors();
    }

    private final MessagesToDelete messagesToDelete;
    private final MessagesDeleterDelegate delegate;
    private boolean wasError = false;

    private AllMessagesDeleter(MessagesToDelete messagesToDelete, MessagesDeleterDelegate delegate) {
        this.messagesToDelete = messagesToDelete;
        this.delegate = delegate;
    }

    static void deleteMessages(MessagesToDelete messagesToDelete, MessagesDeleterDelegate delegate) {
        AllMessagesDeleter deleter = new AllMessagesDeleter(messagesToDelete, delegate);
        deleter.processMessagesToDelete();
    }

    private void processMessagesToDelete() {
        PartisanLog.d("[FindMessages] deletion started");
        int accountNum = messagesToDelete.getAccountNum();
        for (FindMessagesChatData chatData : messagesToDelete) {
            ChatMessagesDeleter.processChat(accountNum, chatData, this::chatProcessed);
        }
        checkIsAllMessagesProcessed();
    }

    private void chatProcessed(long chatId, boolean wasError) {
        this.wasError |= wasError;
        PartisanLog.d("[FindMessages] deletion started");
        messagesToDelete.remove(chatId);
        checkIsAllMessagesProcessed();
    }

    private void checkIsAllMessagesProcessed() {
        if (messagesToDelete.isEmpty()) {
            PartisanLog.d("[FindMessages] all chats were processed");
            if (!wasError) {
                PartisanLog.d("[FindMessages] success");
                delegate.onMessagesDeleted();
            } else {
                PartisanLog.d("[FindMessages] was error");
                delegate.onMessagesDeletedWithErrors();
            }
        }
    }
}
