package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RemoveChatsAction implements Action {
    public ArrayList<Integer> chatsToRemove = new ArrayList<>();
    public int accountNum = 0;

    public RemoveChatsAction() {}

    public RemoveChatsAction(int accountNum, ArrayList<Integer> chatsToRemove) {
        this.accountNum = accountNum;
        this.chatsToRemove = chatsToRemove;
    }

    public void execute() {
        if (chatsToRemove.isEmpty()) {
            return;
        }
        AccountInstance account = AccountInstance.getInstance(accountNum);
        MessagesController messageController = account.getMessagesController();
        for (Integer id : chatsToRemove) {
            TLRPC.Chat chat;
            TLRPC.User user = null;
            if (id > 0) {
                user = messageController.getUser(id);
                chat = null;
            } else {
                chat = messageController.getChat(-id);
            }
            if (chat != null) {
                if (ChatObject.isNotInChat(chat)) {
                    messageController.deleteDialog(id, 0, false);
                } else {
                    TLRPC.User currentUser = messageController.getUser(account.getUserConfig().getClientUserId());
                    messageController.deleteUserFromChat((int) -id, currentUser, null);
                }
            } else {
                messageController.deleteDialog(id, 0, false);
                boolean isBot = user != null && user.bot && !MessagesController.isSupportUser(user);
                if (isBot) {
                    messageController.blockPeer(id);
                }
            }
        }
        chatsToRemove.clear();
        SharedConfig.saveConfig();
    }
}
