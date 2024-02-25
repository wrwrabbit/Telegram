package org.telegram.messenger.partisan.messageinterception;

import org.telegram.tgnet.TLRPC;

public interface MessageInterceptor {
    InterceptionResult interceptMessage(int accountNum, TLRPC.Message message);
}
