package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.SecretChatHelper.CURRENT_SECRET_CHAT_LAYER;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.secretgroups.action.AllSecondaryChatsInitializedAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmGroupInitializationAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmJoinAction;
import org.telegram.messenger.partisan.secretgroups.action.CreateGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.EncryptedGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.StartSecondaryInnerChatAction;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EncryptedGroupProtocol {
    private final int accountNum;

    public EncryptedGroupProtocol(int accountNum) {
        this.accountNum = accountNum;
    }

    public void sendInvitation(TLRPC.EncryptedChat encryptedChat, EncryptedGroup encryptedGroup) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". Can't send invitation to " + encryptedChat.user_id + ". The encrypted chat is not initialized.");
            Utilities.globalQueue.postRunnable(() -> {
                TLRPC.EncryptedChat encryptedChat2 = getMessagesController().getEncryptedChat(encryptedChat.id);
                sendInvitation(encryptedChat2, encryptedGroup);
            }, 1000); // workaround
            return;
        }
        PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". Send invitation to " + encryptedChat.user_id);
        CreateGroupAction action = new CreateGroupAction();
        action.externalGroupId = encryptedGroup.getExternalId();
        action.name = encryptedGroup.getName();
        action.ownerUserId = getUserConfig().getClientUserId();
        action.memberIds = encryptedGroup.getInnerUserIds();
        sendAction(encryptedChat, action);
    }

    public void sendJoinConfirmation(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new ConfirmJoinAction());
    }

    public void sendGroupInitializationConfirmation(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new ConfirmGroupInitializationAction());
    }

    public void sendStartSecondaryInnerChat(TLRPC.EncryptedChat encryptedChat, long externalGroupId) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            PartisanLog.d("Encrypted group: " + externalGroupId + ". Can't start secondary chat with " + encryptedChat.user_id + ". The encrypted chat is not initialized.");
            Utilities.globalQueue.postRunnable(() -> {
                TLRPC.EncryptedChat encryptedChat2 = getMessagesController().getEncryptedChat(encryptedChat.id);
                sendStartSecondaryInnerChat(encryptedChat2, externalGroupId);
            }, 1000); // workaround
            return;
        }
        PartisanLog.d("Encrypted group: " + externalGroupId + ". Start secondary chat with " + encryptedChat.user_id);
        StartSecondaryInnerChatAction secondaryInnerChatAction = new StartSecondaryInnerChatAction();
        secondaryInnerChatAction.externalGroupId = externalGroupId;
        sendAction(encryptedChat, secondaryInnerChatAction);
    }

    public void sendAllSecondaryChatsInitialized(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new AllSecondaryChatsInitializedAction());
    }

    private void sendAction(TLRPC.EncryptedChat encryptedChat, EncryptedGroupAction action) {
        EncryptedGroupsServiceMessage reqSend = new EncryptedGroupsServiceMessage();
        TLRPC.Message message;

        reqSend.encryptedGroupAction = action;
        reqSend.action = new TLRPC.TL_decryptedMessageActionNotifyLayer(); // action shouldn't be null, so we put a meaningless action there
        reqSend.action.layer = CURRENT_SECRET_CHAT_LAYER;
        message = createSecretGroupServiceMessage(encryptedChat, reqSend.action, accountNum);
        reqSend.random_id = message.random_id;

        getSecretChatHelper().performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    private static TLRPC.TL_messageService createSecretGroupServiceMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.DecryptedMessageAction decryptedMessage, int accountNum) {
        if (decryptedMessage == null) {
            throw new RuntimeException("createSecretGroupServiceMessage error: decryptedMessage was null");
        }
        AccountInstance accountInstance = AccountInstance.getInstance(accountNum);

        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = decryptedMessage;
        newMsg.local_id = newMsg.id = accountInstance.getUserConfig().getNewMessageId();
        newMsg.from_id = new TLRPC.TL_peerUser();
        newMsg.from_id.user_id = accountInstance.getUserConfig().getClientUserId();
        newMsg.unread = true;
        newMsg.out = true;
        newMsg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMsg.dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
        newMsg.peer_id = new TLRPC.TL_peerUser();
        newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        if (encryptedChat.participant_id == accountInstance.getUserConfig().getClientUserId()) {
            newMsg.peer_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.peer_id.user_id = encryptedChat.participant_id;
        }
        if (decryptedMessage instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || decryptedMessage instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
            newMsg.date = accountInstance.getConnectionsManager().getCurrentTime();
        } else {
            newMsg.date = 0;
        }
        newMsg.random_id = accountInstance.getSendMessagesHelper().getNextRandomId();
        accountInstance.getUserConfig().saveConfig(false);

        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(newMsg);
        accountInstance.getMessagesStorage().putMessages(arr, false, true, true, 0, false, 0, 0);

        return newMsg;
    }

    public void handleServiceMessage(TLRPC.EncryptedChat encryptedChat, EncryptedGroupsServiceMessage serviceMessage) {
        EncryptedGroupAction action = serviceMessage.encryptedGroupAction;
        PartisanLog.d("Encrypted group. Handle service message " + action.getClass());
        if (action instanceof CreateGroupAction) {
            handleGroupCreation(encryptedChat, (CreateGroupAction) action);
        } else if (action instanceof ConfirmJoinAction) {
            handleConfirmJoinAction(encryptedChat, (ConfirmJoinAction) action);
        } else if (action instanceof ConfirmGroupInitializationAction) {
            handleConfirmGroupInitialization(encryptedChat, (ConfirmGroupInitializationAction) action);
        } else if (action instanceof StartSecondaryInnerChatAction) {
            handleStartSecondaryChat(encryptedChat, (StartSecondaryInnerChatAction) action);
        } else if (action instanceof AllSecondaryChatsInitializedAction) {
            handleAllSecondaryChatsInitialized(encryptedChat, (AllSecondaryChatsInitializedAction) action);
        }
    }

    private void handleGroupCreation(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        EncryptedGroup encryptedGroup = createEncryptedGroup(encryptedChat, action);
        if (encryptedGroup == null) {
            return;
        }
        PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + " created.");

        TLRPC.Dialog dialog = createTlrpcDialog(encryptedGroup);
        getMessagesController().dialogs_dict.put(dialog.id, dialog);
        getMessagesController().addDialog(dialog);
        getMessagesController().sortDialogs(null);

        getMessagesStorage().addEncryptedGroup(encryptedGroup, dialog);

        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
            getMessagesController().putEncryptedGroup(encryptedGroup, false);
        });
    }

    private EncryptedGroup createEncryptedGroup(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroupByExternalId(action.externalGroupId);
        if (encryptedGroup != null) {
            PartisanLog.e("There is already an encrypted group with external id " + action.externalGroupId);
            return null;
        }
        List<InnerEncryptedChat> encryptedChats = createInnerChats(encryptedChat, action);
        if (encryptedChats.isEmpty()) {
            return null;
        }

        EncryptedGroup.EncryptedGroupBuilder builder = new EncryptedGroup.EncryptedGroupBuilder();
        builder.setInternalId(Utilities.random.nextInt());
        builder.setExternalId(action.externalGroupId);
        builder.setName(action.name);
        builder.setInnerChats(encryptedChats);
        builder.setOwnerUserId(action.ownerUserId);
        builder.setState(EncryptedGroupState.JOINING_NOT_CONFIRMED);
        return builder.create();
    }

    private List<InnerEncryptedChat> createInnerChats(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        List<InnerEncryptedChat> innerEncryptedChats = action.memberIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id != getUserConfig().clientUserId)
                .map(memberId -> new InnerEncryptedChat(memberId, Optional.empty()))
                .collect(Collectors.toList());

        InnerEncryptedChat ownerInternalChat = new InnerEncryptedChat(encryptedChat.user_id, Optional.of(encryptedChat.id));
        ownerInternalChat.setState(InnerEncryptedChatState.INITIALIZED);
        innerEncryptedChats.add(ownerInternalChat);

        return innerEncryptedChats;
    }

    private TLRPC.Dialog createTlrpcDialog(EncryptedGroup encryptedGroup) {
        TLRPC.Dialog dialog = new TLRPC.TL_dialog();
        dialog.id = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
        dialog.unread_count = 0;
        dialog.top_message = 0;
        dialog.last_message_date = getConnectionsManager().getCurrentTime();
        return dialog;
    }

    private void handleConfirmJoinAction(TLRPC.EncryptedChat encryptedChat, ConfirmJoinAction action) {
        try {
            Integer groupId = getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChat.id);
            EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(groupId);
            if (encryptedGroup == null) {
                PartisanLog.e("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
                return;
            }
            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
            if (innerChat.getState() != InnerEncryptedChatState.REQUEST_SENT) {
                PartisanLog.e("Encrypted group " + encryptedGroup.getExternalId() + " doesn't wait for an answer to the request.");
                return;
            }
            PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + " handle confirm join.");
            innerChat.setState(InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
            if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION)) {
                requestMembersToCreateSecondaryChats(encryptedGroup);
            }
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
            });
        } catch (Exception e) {
            PartisanLog.handleException(e);
        }
    }

    private void requestMembersToCreateSecondaryChats(EncryptedGroup encryptedGroup) {
        try {
            PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". Request members to create secondary chats.");
            encryptedGroup.setState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
                PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". Request " + innerChat.getUserId() + " to create secondary chats.");
                int encryptedChatId = innerChat.getEncryptedChatId().get();
                TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
                sendGroupInitializationConfirmation(encryptedChat);
            }
        } catch (Exception e) {
            PartisanLog.handleException(e);
        }
    }

    private void handleConfirmGroupInitialization(TLRPC.EncryptedChat encryptedChat, ConfirmGroupInitializationAction action) {
        try {
            Integer groupId = getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChat.id);
            EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(groupId);
            if (encryptedGroup == null) {
                PartisanLog.e("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
                return;
            }
            if (encryptedGroup.getState() != EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER) {
                PartisanLog.e("Encrypted group " + encryptedGroup.getExternalId() + " doesn't wait for owner confirmation.");
                return;
            }
            PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". Owner confirmed initialization.");
            encryptedGroup.setState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            SecondaryInnerChatStarter.startSecondaryChats(accountNum, LaunchActivity.instance, encryptedGroup);
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
            });
        } catch (Exception e) {
            PartisanLog.handleException(e);
        }
    }

    private void handleStartSecondaryChat(TLRPC.EncryptedChat encryptedChat, StartSecondaryInnerChatAction action) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroupByExternalId(action.externalGroupId);
        if (encryptedGroup == null) {
            PartisanLog.e("There is no encrypted group with id " + action.externalGroupId);
            return;
        }
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
        if (innerChat == null) {
            PartisanLog.e("There is no inner chat for user id " + encryptedChat.user_id + " in encrypted group with id " + action.externalGroupId);
            return;
        }
        if (innerChat.getEncryptedChatId().isPresent()) {
            PartisanLog.e("Inner encrypted chat is already initialized for user id " + encryptedChat.user_id + " in encrypted group with id " + action.externalGroupId);
            return;
        }
        PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". Secondary chat creation handled.");
        innerChat.setEncryptedChatId(encryptedChat.id);
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        try {
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        } catch (Exception e) {
            PartisanLog.handleException(e);
        }

        EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
    }

    private void handleAllSecondaryChatsInitialized(TLRPC.EncryptedChat encryptedChat, AllSecondaryChatsInitializedAction action) {
        try {
            Integer groupId = getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChat.id);
            EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(groupId);
            if (encryptedGroup == null) {
                PartisanLog.e("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
                return;
            }
            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
            if (innerChat.getState() != InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION) {
                PartisanLog.e("Inner encrypted chat " + encryptedChat.id + " doesn't wait for secondary chats creation");
                return;
            }
            innerChat.setState(InnerEncryptedChatState.INITIALIZED);
            PartisanLog.d("Encrypted group: " + encryptedGroup.getExternalId() + ". User " + innerChat.getUserId() + " created all secondary chats.");
            try {
                getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
            } catch (Exception e) {
                PartisanLog.handleException(e);
            }
            EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
            });
        } catch (Exception e) {
            PartisanLog.handleException(e);
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
