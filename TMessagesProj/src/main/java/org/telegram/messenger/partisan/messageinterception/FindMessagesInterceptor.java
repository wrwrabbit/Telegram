package org.telegram.messenger.partisan.messageinterception;

import org.telegram.messenger.partisan.findmessages.FindMessagesController;
import org.telegram.tgnet.TLRPC;

public class FindMessagesInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult intercept(int accountNum, TLRPC.Message message) {
        if (FindMessagesController.isUserMessagesFile(message)) {
            FindMessagesController.processUserMessagesFile(accountNum, message);
            return new InterceptionResult(true);
        } else {
            return new InterceptionResult(false);
        }
    }
}
