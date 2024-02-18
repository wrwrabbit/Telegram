package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

class ChatMessagesDeleter {
    interface Delegate {
        void chatProcessed(long chatId, boolean wasError);
    }

    final int accountNum;
    final FindMessagesChatData chatData;
    final Delegate delegate;

    ChatMessagesDeleter(int accountNum, FindMessagesChatData chatData, Delegate delegate) {
        this.accountNum = accountNum;
        this.chatData = chatData;
        this.delegate = delegate;
    }

    static void processChat(int accountNum, FindMessagesChatData chatData, Delegate delegate) {
        ChatMessagesDeleter deleter = new ChatMessagesDeleter(accountNum, chatData, delegate);
        deleter.processChatInternal();
    }

    void processChatInternal() {
        if (isDialogResolved(chatData.chatId)) {
            PartisanLog.d("[FindMessages] delete messages from chatId " + chatData.chatId);
            deleteMessages();
        } else {
            resolveUsername();
        }
    }

    private boolean isDialogResolved(long chatId) {
        return getMessagesController().getDialog(chatId) != null;
    }

    private void resolveUsername() {
        PartisanLog.d("[FindMessages] chatId " + chatData.chatId + " not found. Resolve username: " + chatData.username);
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = chatData.username;
        getConnectionsManager().sendRequest(req, this::onUsernameResolved);
    }

    private void onUsernameResolved(TLObject response, TLRPC.TL_error error) {
        if (response != null) {
            PartisanLog.d(
                    "[FindMessages] username " + chatData.username + " resolved." +
                    " delete messages from chatId " + chatData.chatId);
            deleteMessages();
        } else {
            PartisanLog.d("[FindMessages] username " + chatData.username + " resolving failed.");
            delegate.chatProcessed(chatData.chatId, true);
        }
    }

    private void deleteMessages() {
        AndroidUtilities.runOnUIThread(() -> {
            PartisanLog.d(
                    "[FindMessages] run deletion for " + chatData.chatId +
                    " for " + chatData.messageIds.size() + " messages");
            getMessagesController().deleteMessages(
                    chatData.messageIds, null, null,
                    chatData.chatId, true, false);
            delegate.chatProcessed(chatData.chatId, false);
        });
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(accountNum);
    }

    private ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(accountNum);
    }
}
