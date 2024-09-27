package org.telegram.messenger.partisan.secretgroups;

import android.content.Context;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SecretGroupStarter {
    private final int accountNum;
    private final Context context;
    private final List<TLRPC.User> users = new LinkedList<>();
    private final String name;
    private final List<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
    private final Consumer<EncryptedGroup> callback;

    public SecretGroupStarter(int accountNum, Context context, List<TLRPC.User> users, String name, Consumer<EncryptedGroup> callback) {
        this.accountNum = accountNum;
        this.context = context;
        this.users.addAll(users);
        this.name = name;
        this.callback = callback;
    }

    public static void startSecretGroup(int accountNum, Context context, List<TLRPC.User> users, String name, Consumer<EncryptedGroup> callback) {
        if (users == null || users.isEmpty() || context == null) {
            return;
        }
        new SecretGroupStarter(accountNum, context, users, name, callback).start();
    }

    public void start() {
        checkInternalEncryptedChats();
    }

    private void checkInternalEncryptedChats() {
        if (users.size() != encryptedChats.size()) {
            startNextInternalEncryptedChat();
        } else {
            onAllEncryptedChatsCreated();
        }
    }

    private void startNextInternalEncryptedChat() {
        int currentUserIndex = encryptedChats.size();
        int delay = encryptedChats.isEmpty() ? 0 : 10*1000;
        TLRPC.User user = users.get(currentUserIndex);
        Utilities.globalQueue.postRunnable(
                () -> getSecretChatHelper().startSecretChat(context, user, this::onInternalEncryptedChatStarted),
                delay
        );
    }

    private void onInternalEncryptedChatStarted(TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat != null) {
            encryptedChats.add(encryptedChat);
            checkInternalEncryptedChats();
        } else {
            callback.accept(null);
        }
    }

    private void onAllEncryptedChatsCreated() {
        AndroidUtilities.runOnUIThread(() -> {
            EncryptedGroup encryptedGroup = new EncryptedGroup();
            encryptedGroup.id = Utilities.random.nextInt();
            encryptedGroup.encryptedChatsIds = encryptedChats.stream()
                    .filter(Objects::nonNull)
                    .map(encryptedChat -> encryptedChat.id)
                    .collect(Collectors.toList());
            if (encryptedGroup.encryptedChatsIds.isEmpty()) {
                callback.accept(null);
                return;
            }
            encryptedGroup.name = name;

            TLRPC.Dialog dialog = new TLRPC.TL_dialog();
            dialog.id = DialogObject.makeEncryptedDialogId(encryptedGroup.id);
            dialog.unread_count = 0;
            dialog.top_message = 0;
            dialog.last_message_date = getConnectionsManager().getCurrentTime();
            getMessagesController().dialogs_dict.put(dialog.id, dialog);
            getMessagesController().addDialog(dialog);
            getMessagesController().sortDialogs(null);


            MessagesStorage.getInstance(accountNum)
                    .putEncryptedGroup(encryptedGroup, dialog);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getMessagesController().putEncryptedGroup(encryptedGroup, false);
            callback.accept(encryptedGroup);
        });
    }

    private ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(accountNum);
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(accountNum);
    }

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(accountNum);
    }

    private SecretChatHelper getSecretChatHelper() {
        return SecretChatHelper.getInstance(accountNum);
    }
}
