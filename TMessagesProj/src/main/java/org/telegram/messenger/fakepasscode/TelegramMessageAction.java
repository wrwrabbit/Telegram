package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.Map;

public class TelegramMessageAction implements Action {
    public boolean enabled = false;
    public Map<Integer, String> chatsToSendingMessages = new HashMap<>();
    public int accountNum = 0;

    @Override
    public void execute() {
        if (chatsToSendingMessages.isEmpty()) {
            return;
        }
        SendMessagesHelper messageSender = SendMessagesHelper.getInstance(accountNum);
        for (Map.Entry<Integer, String> entry : chatsToSendingMessages.entrySet()) {
            messageSender.sendMessage(entry.getValue(), entry.getKey(), null, null, null, false,
                        null, null, null, true, 0);
        }

        SharedConfig.saveConfig();
    }
}
