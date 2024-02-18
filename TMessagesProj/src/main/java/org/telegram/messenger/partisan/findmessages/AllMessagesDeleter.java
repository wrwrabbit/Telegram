package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.partisan.PartisanLog;

public class AllMessagesDeleter {
    private final Runnable onSuccess;
    private final Runnable onError;
    private final MessagesToDelete messagesToDelete;
    private boolean wasError = false;

    private AllMessagesDeleter(MessagesToDelete messagesToDelete, Runnable onSuccess, Runnable onError) {
        this.messagesToDelete = messagesToDelete;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    static void deleteMessages(MessagesToDelete messagesToDelete, Runnable onSuccess, Runnable onError) {
        AllMessagesDeleter deleter = new AllMessagesDeleter(messagesToDelete, onSuccess, onError);
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
                onSuccess.run();
            } else {
                PartisanLog.d("[FindMessages] was error");
                onError.run();
            }
        }
    }
}
