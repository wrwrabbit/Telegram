package org.telegram.messenger.partisan.findmessages;

import org.telegram.tgnet.TLRPC;

class MessagesToDeleteLoadingException extends RuntimeException  {
    MessagesToDeleteLoadingException(Exception cause) {
        super(cause);
    }
}
