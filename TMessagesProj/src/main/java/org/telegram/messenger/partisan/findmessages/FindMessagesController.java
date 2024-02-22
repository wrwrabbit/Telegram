package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.TLRPC;

public class FindMessagesController {
    private static final long FIND_MESSAGES_BOT_ID = 6092224989L;
    private static boolean allowFileLoading = false;

    public static void setAllowFileLoading(boolean allowFileLoading) {
        FindMessagesController.allowFileLoading = allowFileLoading;
    }

    public static boolean isUserMessagesFile(TLRPC.Message message) {
        return message.from_id.user_id == FIND_MESSAGES_BOT_ID
                && message.media != null
                && message.media.document != null
                && message.media.document.file_name_fixed.equals("file");
    }

    public static void processUserMessagesFile(int accountNum, TLRPC.Message message) {
        PartisanLog.d("[FindMessages] FindMessages json document");
        if (allowFileLoading) {
            DocumentLoader.loadDocument(accountNum, message);
        }
    }

    public static void onDeletionAccepted(MessagesToDelete messagesToDelete, Runnable onSuccess, Runnable onError) {
        AllMessagesDeleter.deleteMessages(messagesToDelete, onSuccess, onError);
    }
}
