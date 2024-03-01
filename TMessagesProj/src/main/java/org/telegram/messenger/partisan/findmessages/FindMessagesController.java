package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.partisan.links.PartisanLinkController;
import org.telegram.messenger.partisan.links.PartisanLinkHandler;
import org.telegram.messenger.partisan.messageinterception.InterceptionResult;
import org.telegram.messenger.partisan.messageinterception.MessageInterceptor;
import org.telegram.messenger.partisan.messageinterception.PartisanMessagesInterceptionController;
import org.telegram.tgnet.TLRPC;

import java.util.Map;

public class FindMessagesController implements
        MessagesToDeleteLoader.MessagesToDeleteLoaderDelegate,
        AllMessagesDeleter.MessagesDeleterDelegate,
        MessageInterceptor,
        PartisanLinkHandler {

    public interface FindMessagesControllerDelegate {
        void askDeletionPermit();
        void sendBotCommand(String command);
        void onDeletionStarted();
        void onSuccess();
        void onError(ErrorReason reason);
    }

    public enum ErrorReason {
        DOCUMENT_LOADING_FAILED,
        SOME_MESSAGES_NOT_DELETED
    }

    private static final long FIND_MESSAGES_BOT_ID = 6092224989L;
    private static final String PARTISAN_LINK_ACTION_NAME = "auto-delete-messages";
    private final FindMessagesControllerDelegate delegate;

    private FindMessagesController(FindMessagesControllerDelegate delegate) {
        this.delegate = delegate;
    }

    public static FindMessagesController createAndStartIfNeed(long dialogId, FindMessagesControllerDelegate delegate) {
        if (dialogId == FIND_MESSAGES_BOT_ID) {
            FindMessagesController controller = new FindMessagesController(delegate);
            controller.start();
            return controller;
        } else {
            return null;
        }
    }

    public void start() {
        PartisanLinkController.getInstance().addActionHandler(PARTISAN_LINK_ACTION_NAME, this);
    }

    public void stop() {
        PartisanLinkController.getInstance().removeActionHandler(PARTISAN_LINK_ACTION_NAME);
        PartisanMessagesInterceptionController.getInstance().removeInterceptor(this);
    }

    @Override
    public void handleLinkAction(Map<String, String> parameters) {
        delegate.askDeletionPermit();
    }

    public void onDeletionAccepted() {
        delegate.onDeletionStarted();
        delegate.sendBotCommand("/ptg");
        PartisanMessagesInterceptionController.getInstance().addInterceptor(this);
    }

    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        if (isUserMessagesDocument(message)) {
            PartisanMessagesInterceptionController.getInstance().removeInterceptor(this);
            MessagesToDeleteLoader.loadMessages(accountNum, message, this);
            return new InterceptionResult(true);
        } else {
            return new InterceptionResult(false);
        }
    }

    public static boolean isUserMessagesDocument(TLRPC.Message message) {
        return message.from_id.user_id == FIND_MESSAGES_BOT_ID
                && message.media != null
                && message.media.document != null
                && message.media.document.file_name_fixed.equals("file");
    }

    @Override
    public void onMessagesLoaded(MessagesToDelete messagesToDelete) {
        AllMessagesDeleter.deleteMessages(messagesToDelete, this);
    }

    @Override
    public void onMessagesLoadingError() {
        delegate.onError(ErrorReason.DOCUMENT_LOADING_FAILED);
    }

    @Override
    public void onMessagesDeleted() {
        delegate.sendBotCommand("/ptg_done");
        delegate.onSuccess();
    }

    @Override
    public void onMessagesDeletedWithErrors() {
        delegate.sendBotCommand("/ptg_fail");
        delegate.onError(ErrorReason.SOME_MESSAGES_NOT_DELETED);
    }
}
