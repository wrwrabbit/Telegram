package org.telegram.messenger.partisan.messageinterception;

import com.google.common.base.Strings;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.tgnet.TLRPC;

public class FakePasscodeActivationInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        tryActivateAnyFakePasscode(accountNum, message);
        return new InterceptionResult(false);
    }

    private synchronized void tryActivateAnyFakePasscode(int accountNum, TLRPC.Message message) {
        if (message == null) {
            return;
        }
        String messageText = message.message;
        if (Strings.isNullOrEmpty(messageText) || messageSentByCurrentUser(accountNum, message)) {
            return;
        }
        for (FakePasscode fakePasscode : SharedConfig.fakePasscodes) {
            boolean activated = fakePasscode.tryActivateByMessage(message);
            if (activated) {
                break;
            }
        }
    }

    private boolean messageSentByCurrentUser(int accountNum, TLRPC.Message message) {
        Long senderUserId = message.from_id != null ? message.from_id.user_id : null;
        return senderUserId != null
                && senderUserId == UserConfig.getInstance(accountNum).clientUserId;
    }

}
