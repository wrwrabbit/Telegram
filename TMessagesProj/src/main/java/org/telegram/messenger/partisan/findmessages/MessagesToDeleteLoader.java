package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.partisan.NotCachedDocumentLoader;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

class MessagesToDeleteLoader implements NotCachedDocumentLoader.DocumentLoaderDelegate {
    interface MessagesToDeleteLoaderDelegate {
        void onMessagesLoaded(MessagesToDelete messagesToDelete);
        void onMessagesLoadingError();
    }

    private final int accountNum;
    private final TLRPC.Message message;
    private final MessagesToDeleteLoaderDelegate delegate;

    private MessagesToDeleteLoader(int accountNum, TLRPC.Message message, MessagesToDeleteLoaderDelegate delegate) {
        this.accountNum = accountNum;
        this.message = message;
        this.delegate = delegate;
    }

    static void loadMessages(int accountNum, TLRPC.Message message, MessagesToDeleteLoaderDelegate delegate) {
        PartisanLog.d("[FindMessages] process document");
        new MessagesToDeleteLoader(accountNum, message, delegate).loadMessagesInternal();
    }

    void loadMessagesInternal() {
        NotCachedDocumentLoader.loadDocument(accountNum, message.media.document, this);
    }

    @Override
    public void onDocumentLoaded(byte[] content) {
        deleteFileMessage();
        try {
            MessagesToDelete messagesToDelete = parseFile(content);
            notifyMessagesParsed(messagesToDelete);
        } catch (MessagesToDeleteLoadingException e) {
            delegate.onMessagesLoadingError();
        }
    }

    private MessagesToDelete parseFile(byte[] fileContent) {
        byte[] decryptedPayload = MessagesToDeleteDecryptor.decrypt(fileContent, message.message);
        return MessagesToDeleteParser.parse(accountNum, decryptedPayload);
    }

    private void notifyMessagesParsed(MessagesToDelete messagesToDelete) {
        delegate.onMessagesLoaded(messagesToDelete);
    }

    @Override
    public void onDocumentLoadingError() {
        deleteFileMessage();
        delegate.onMessagesLoadingError();
    }

    private void deleteFileMessage() {
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<Integer> ids = new ArrayList<>();
            ids.add(message.id);
            MessagesController.getInstance(accountNum).deleteMessages(
                    ids, null, null,
                    message.dialog_id, 0, true, ChatActivity.MODE_DEFAULT);
        });
    }
}
