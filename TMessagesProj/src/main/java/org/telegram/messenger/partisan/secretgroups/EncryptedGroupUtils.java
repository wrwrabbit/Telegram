package org.telegram.messenger.partisan.secretgroups;

import android.content.DialogInterface;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupUtils {
    public static void checkAllEncryptedChatsCreated(EncryptedGroup encryptedGroup, int accountNum) {
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.INITIALIZED)) {
            log(encryptedGroup, accountNum, "All encrypted chats initialized.");
            encryptedGroup.setState(EncryptedGroupState.INITIALIZED);
            try {
                MessagesStorage.getInstance(accountNum).updateEncryptedGroup(encryptedGroup);
            } catch (Exception e) {
                PartisanLog.handleException(e);
            }
            if (encryptedGroup.getOwnerUserId() != UserConfig.getInstance(accountNum).clientUserId) {
                MessagesController messagesController = MessagesController.getInstance(accountNum);
                int ownerEncryptedChatId = encryptedGroup.getOwnerEncryptedChatId();
                TLRPC.EncryptedChat ownerEncryptedChat = messagesController.getEncryptedChat(ownerEncryptedChatId);
                new EncryptedGroupProtocol(accountNum).sendAllSecondaryChatsInitialized(ownerEncryptedChat);
            }
        } else if (PartisanLog.logsAllowed()) {
            String notInitializedInnerChats = encryptedGroup.getInnerChats().stream()
                    .filter(innerChat -> innerChat.getState() != InnerEncryptedChatState.INITIALIZED)
                    .map(innerChat -> Long.toString(innerChat.getUserId()))
                    .collect(Collectors.joining(", "));
            log(encryptedGroup, accountNum, "NOT all encrypted chats initialized: " + notInitializedInnerChats + ".");
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

    public static void getEncryptedGroupIdByInnerEncryptedDialogIdAndExecute(long dialogId, int account, Consumer<Integer> action) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            try {
                Integer encryptedGroupId = MessagesStorage.getInstance(account)
                        .getEncryptedGroupIdByInnerEncryptedChatId(DialogObject.getEncryptedChatId(dialogId));
                if (encryptedGroupId != null) {
                    action.accept(encryptedGroupId);
                }
            } catch (Exception e) {
                PartisanLog.handleException(e);
            }
        }
    }

    public static void updateEncryptedGroupUnreadCount(int encryptedGroupId, int account) {
        MessagesController messagesController = MessagesController.getInstance(account);

        EncryptedGroup encryptedGroup = messagesController.getEncryptedGroup(encryptedGroupId);
        TLRPC.Dialog encryptedGroupDialog = messagesController.getDialog(DialogObject.makeEncryptedDialogId(encryptedGroupId));
        encryptedGroupDialog.unread_count = 0;
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            if (innerChat.getDialogId().isPresent()) {
                TLRPC.Dialog innerDialog = messagesController.getDialog(innerChat.getDialogId().get());
                if (innerDialog != null) {
                    encryptedGroupDialog.unread_count += innerDialog.unread_count;
                }
            }
        }
        MessagesStorage.getInstance(account).updateEncryptedGroupDialog(encryptedGroupDialog);
    }

    public static void updateEncryptedGroupLastMessageDate(int encryptedGroupId, int account) {
        MessagesController messagesController = MessagesController.getInstance(account);

        EncryptedGroup encryptedGroup = messagesController.getEncryptedGroup(encryptedGroupId);
        TLRPC.Dialog encryptedGroupDialog = messagesController.getDialog(DialogObject.makeEncryptedDialogId(encryptedGroupId));
        encryptedGroupDialog.last_message_date = encryptedGroup.getInnerChats().stream()
                .map(InnerEncryptedChat::getDialogId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(messagesController::getDialog)
                .filter(Objects::nonNull)
                .mapToInt(dialog -> dialog.last_message_date)
                .max()
                .orElse(0);
        MessagesStorage.getInstance(account).updateEncryptedGroupDialog(encryptedGroupDialog);
    }

    public static void showSecretGroupJoinDialog(EncryptedGroup encryptedGroup, BaseFragment fragment, int accountNum, Runnable onJoined) {
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        MessagesStorage messagesStorage = MessagesStorage.getInstance(accountNum);

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        TLRPC.User ownerUser = messagesController.getUser(encryptedGroup.getOwnerUserId());
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.SecretGroupJoiningConfirmation, UserObject.getUserName(ownerUser))));
        builder.setPositiveButton(LocaleController.getString(R.string.JoinSecretGroup), (dialog, which) -> {
            encryptedGroup.setState(EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER);
            try {
                messagesStorage.updateEncryptedGroup(encryptedGroup);
            } catch (Exception e) {
                PartisanLog.handleException(e);
            }
            TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(encryptedGroup.getOwnerEncryptedChatId());
            log(encryptedGroup, accountNum, "Send join confirmation.");
            new EncryptedGroupProtocol(accountNum).sendJoinConfirmation(encryptedChat);

            if (onJoined != null) {
                onJoined.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.DeclineJoiningToSecretGroup), (dialog, which) -> {
            long dialogId = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
            messagesController.deleteDialog(dialogId, 0, false);
        });
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void showNotImplementedDialog(BaseFragment fragment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(LocaleController.getString(R.string.ThisFeatureIsNotImplemented));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
    }

    static void log(int account, String message) {
        log((Long)null, account, message);
    }

    static void log(@Nullable EncryptedGroup encryptedGroup, int account, String message) {
        Long externalId = encryptedGroup != null ? encryptedGroup.getExternalId() : null;
        log(externalId, account, message);
    }

    static void log(@Nullable Long encryptedGroupExternalId, int account, String message) {
        if (encryptedGroupExternalId != null) {
            PartisanLog.d("Account: " + account + ". Encrypted group: " + encryptedGroupExternalId + ". " + message);
        } else {
            PartisanLog.d("Account: " + account + ". Encrypted group: unknown. " + message);
        }
    }
}