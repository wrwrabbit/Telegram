package org.telegram.messenger.partisan.secretgroups;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.TLRPC;

public class EncryptedGroupUtils {
    public static void checkAllEncryptedChatsCreated(EncryptedGroup encryptedGroup, int accountNum) {
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.INITIALIZED)) {
            encryptedGroup.setState(EncryptedGroupState.INITIALIZED);
            try {
                MessagesStorage.getInstance(accountNum).updateEncryptedGroup(encryptedGroup);
            } catch (Exception e) {
                PartisanLog.handleException(e);
            }
            if (encryptedGroup.getOwnerUserId() != UserConfig.getInstance(accountNum).clientUserId) {
                InnerEncryptedChat ownerInnerEncryptedChat = encryptedGroup.getInnerChatByUserId(encryptedGroup.getOwnerUserId());
                TLRPC.EncryptedChat ownerEncryptedChat = MessagesController.getInstance(accountNum).getEncryptedChat(ownerInnerEncryptedChat.getEncryptedChatId());
                new EncryptedGroupProtocol(accountNum).sendAllSecondaryChatsInitialized(ownerEncryptedChat);
            }
        }
    }

    public static String getEncryptedStateDescription(EncryptedGroupState state) {
        switch (state) {
            case JOINING_NOT_CONFIRMED:
                return LocaleController.getString(R.string.JoiningNotConfirmed);
            case WAITING_CONFIRMATION_FROM_MEMBERS:
            case WAITING_CONFIRMATION_FROM_OWNER:
                return LocaleController.getString(R.string.WaitingForSecretGroupInitializationConfirmation);
            case WAITING_SECONDARY_CHAT_CREATION:
                return LocaleController.getString(R.string.WaitingForSecondaryChatsCreation);
            case INITIALIZATION_FAILED:
                return LocaleController.getString(R.string.SecretGroupInitializationFailed);
            default:
                throw new RuntimeException("Can't return encrypted group state description for state " + state);
        }
    }
}
