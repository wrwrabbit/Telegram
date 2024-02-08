package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FindMessagesHelper {
    private final int accountNum;
    private final Runnable onSuccess;
    private final Runnable onError;
    private final Map<Long, FindMessagesItem> messagesToDelete;
    private boolean wasError = false;

    private FindMessagesHelper(int accountNum, Map<Long, FindMessagesItem> messagesToDelete, Runnable onSuccess, Runnable onError) {
        this.accountNum = accountNum;
        this.messagesToDelete = new ConcurrentHashMap<>(messagesToDelete);
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    private void processMessagesToDelete() {
        for (FindMessagesItem item : messagesToDelete.values()) {
            if (MessagesController.getInstance(accountNum).getDialog(item.chatId) != null) {
                deleteMessages(item.chatId, item.messageIds);
            } else {
                TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                req.username = item.username;
                ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
                    if (response != null) {
                        deleteMessages(item.chatId, item.messageIds);
                    } else {
                        wasError = true;
                        chatProcessed(item.chatId);
                    }
                });
            }
        }
        checkIsAllMessagesProcessed();
    }

    private void deleteMessages(long chatId, ArrayList<Integer> messageIds) {
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController.getInstance(accountNum).deleteMessages(messageIds, null, null, chatId, true, false);
            chatProcessed(chatId);
        });
    }

    private void chatProcessed(long chatId) {
        messagesToDelete.remove(chatId);
        checkIsAllMessagesProcessed();
    }

    private void checkIsAllMessagesProcessed() {
        if (messagesToDelete.isEmpty()) {
            if (!wasError) {
                onSuccess.run();
            } else {
                onError.run();
            }
        }
    }

    public static void deletionAccepted(int accountNum, Map<Long, FindMessagesItem> messagesToDelete, Runnable onSuccess, Runnable onError) {
        new FindMessagesHelper(accountNum, messagesToDelete, onSuccess, onError).processMessagesToDelete();
    }

    public static boolean checkIsUserMessagesJson(int accountNum, TLRPC.Message message) {
        if (message.from_id.user_id == 6092224989L && message.media != null
                && message.media.document != null
                && message.media.document.file_name_fixed.equals("file")) {
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.findMessagesJsonReceived, message)
            );
            return true;
        } else {
            return false;
        }
    }
}
