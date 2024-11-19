package org.telegram.messenger.partisan.secretgroups;

import android.content.Context;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
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
        checkInnerEncryptedChats();
    }

    private void checkInnerEncryptedChats() {
        if (users.size() != encryptedChats.size()) {
            startNextInnerEncryptedChat();
        } else {
            onAllEncryptedChatsCreated();
        }
    }

    private void startNextInnerEncryptedChat() {
        int currentUserIndex = encryptedChats.size();
        int delay = encryptedChats.isEmpty() ? 0 : 10*1000;
        TLRPC.User user = users.get(currentUserIndex);
        Utilities.globalQueue.postRunnable(
                () -> getSecretChatHelper().startSecretChat(context, user, this::onInnerEncryptedChatStarted),
                delay
        );
    }

    private void onInnerEncryptedChatStarted(TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat != null) {
            encryptedChats.add(encryptedChat);
            checkInnerEncryptedChats();
        } else {
            callback.accept(null);
        }
    }

    private void onAllEncryptedChatsCreated() {
        AndroidUtilities.runOnUIThread(() -> {
            EncryptedGroup encryptedGroup = createEncryptedGroup();
            if (encryptedGroup == null) {
                callback.accept(null);
                return;
            }

            TLRPC.Dialog dialog = createTlrpcDialog(encryptedGroup);
            getMessagesController().dialogs_dict.put(dialog.id, dialog);
            getMessagesController().addDialog(dialog);
            getMessagesController().sortDialogs(null);

            getMessagesStorage().addEncryptedGroup(encryptedGroup, dialog);

            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getMessagesController().putEncryptedGroup(encryptedGroup, false);

            sendInvitations(encryptedGroup);

            callback.accept(encryptedGroup);
        });
    }

    private EncryptedGroup createEncryptedGroup() {
        List<InnerEncryptedChat> encryptedChats = createEncryptedChats();
        if (encryptedChats.isEmpty()) {
            return null;
        }

        EncryptedGroup.EncryptedGroupBuilder builder = new EncryptedGroup.EncryptedGroupBuilder();
        builder.setInternalId(Utilities.random.nextInt());
        builder.setExternalId(Utilities.random.nextLong());
        builder.setName(name);
        builder.setInnerChats(encryptedChats);
        builder.setOwnerUserId(getUserConfig().clientUserId);
        builder.setState(EncryptedGroupState.WAITING_CONFIRMATION_FROM_MEMBERS);
        return builder.create();
    }

    private List<InnerEncryptedChat> createEncryptedChats() {
        return encryptedChats.stream()
                .filter(Objects::nonNull)
                .map(encryptedChat -> new InnerEncryptedChat(encryptedChat.user_id, encryptedChat.id))
                .collect(Collectors.toList());
    }

    private TLRPC.Dialog createTlrpcDialog(EncryptedGroup encryptedGroup) {
        TLRPC.Dialog dialog = new TLRPC.TL_dialog();
        dialog.id = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
        dialog.unread_count = 0;
        dialog.top_message = 0;
        dialog.last_message_date = getConnectionsManager().getCurrentTime();
        return dialog;
    }

    private void sendInvitations(EncryptedGroup encryptedGroup) {
        EncryptedGroupProtocol protocol = new EncryptedGroupProtocol(accountNum);
        for (TLRPC.EncryptedChat encryptedChat : encryptedChats) {
            protocol.sendInvitation(encryptedChat, encryptedGroup);
        }
    }

    private UserConfig getUserConfig() {
        return UserConfig.getInstance(accountNum);
    }

    private ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(accountNum);
    }

    private MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(accountNum);
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
