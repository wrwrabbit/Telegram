package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoveAfterReadingMessages {
    private static class MessageLoader implements NotificationCenter.NotificationCenterDelegate {
        private final int classGuid = ConnectionsManager.generateClassGuid();
        private final Consumer<MessageObject> consumer;

        private MessageLoader(Consumer<MessageObject> consumer) {
            this.consumer = consumer;
        }

        public static void load(int accountNum, long dialogId, int messageId, Consumer<MessageObject> consumer) {
            new MessageLoader(consumer).loadInternal(accountNum, dialogId, messageId);
        }

        public void loadInternal(int accountNum, long dialogId, int messageId) {
            NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.messagesDidLoad);
            MessagesController.getInstance(accountNum).loadMessages(dialogId, 0, false, 1, messageId + 1, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
        }

        @Override
        public synchronized void didReceivedNotification(int id, int account, Object... args) {
            if ((int)args[10] == classGuid) {
                NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagesDidLoad);
                if ((int)args[1] >= 1) {
                    consumer.accept(((ArrayList<MessageObject>)args[2]).get(0));
                }
            }
        }
    }

    public static Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead = new HashMap<>();
    public static Map<String, Integer> delays = new HashMap<>();
    private static final Object sync = new Object();
    private static boolean isLoaded = false;

    public static void load() {
        synchronized (sync) {
            if (isLoaded) {
                return;
            }

            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                ObjectMapper mapper = new ObjectMapper();
                mapper.enableDefaultTyping();
                String messagesToRemoveAsReadString = preferences.getString("messagesToRemoveAsRead", null);
                String delaysString = preferences.getString("delays", null);
                messagesToRemoveAsRead = mapper.readValue(messagesToRemoveAsReadString, HashMap.class);
                delays = mapper.readValue(delaysString, HashMap.class);
                isLoaded = true;
            } catch (Exception ignored) {
            }
        }
    }

    public static void save() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                ObjectMapper mapper = new ObjectMapper();
                mapper.enableDefaultTyping();
                String messagesToRemoveAsReadString = mapper.writeValueAsString(messagesToRemoveAsRead);
                String delaysString = mapper.writeValueAsString(delays);
                editor.putString("messagesToRemoveAsRead", messagesToRemoveAsReadString);
                editor.putString("delays", delaysString);
                editor.commit();
            } catch (Exception ignored) {
            }
        }
    }

    private static void cleanAutoDeletable(int messageId, int currentAccount, long dialogId) {
        load();
        Map<String, List<RemoveAsReadMessage>> curAccountMessages = messagesToRemoveAsRead.get("" + currentAccount);

        if (curAccountMessages == null || curAccountMessages.get("" + dialogId) == null) {
            return;
        }

        for (RemoveAsReadMessage messageToRemove : new ArrayList<>(curAccountMessages.get("" + dialogId))) {
            if (messageToRemove.getId() == messageId) {
                messagesToRemoveAsRead.get("" + currentAccount).get("" + dialogId).remove(messageToRemove);
            }
        }

        if (curAccountMessages.get("" + dialogId) != null
                && curAccountMessages.get("" + dialogId).isEmpty()) {
            messagesToRemoveAsRead.get("" + currentAccount).remove("" + dialogId);
        }
        save();
    }

    public static void checkReadDialogs(int currentAccount) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (!controller.dialogsLoaded) {
            AndroidUtilities.runOnUIThread(() -> checkReadDialogs(currentAccount), 100);
            return;
        }
        load();
        Map<String, List<RemoveAsReadMessage>> dialogsToCheck =
                messagesToRemoveAsRead.get("" + currentAccount);
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog == null) {
                continue;
            }
            List<RemoveAsReadMessage> messagesToCheck = dialogsToCheck.get("" + dialog.id);
            if (messagesToCheck == null) {
                continue;
            }
            if (DialogObject.isEncryptedDialog(dialog.id)) {
                for (RemoveAsReadMessage messageToRemove : messagesToCheck) {
                    MessageLoader.load(currentAccount, dialog.id, messageToRemove.getId(), message -> {
                        if (!message.isUnread()) {
                            startEncryptedDialogDeleteProcess(currentAccount, dialog.id, messageToRemove);
                        }
                    });
                }
            } else {
                readMaxIdUpdated(currentAccount, dialog.id, dialog.read_outbox_max_id);
            }
        }
    }

    public static void notifyMessagesRead(int currentAccount, List<MessageObject> messages) {
        Map<Long, Integer> dialogLastReadIds = new HashMap<>();
        Map<Long, RemoveAsReadMessage> encryptedMessagesToDelete = new HashMap<>();
        messagesToRemoveAsRead.putIfAbsent("" + currentAccount, new HashMap<>());
        Map<String, List<RemoveAsReadMessage>> dialogsToCheck = messagesToRemoveAsRead.putIfAbsent("" + currentAccount, new HashMap<>());
        for (MessageObject message : messages) {
            long dialogId = message.messageOwner.dialog_id;
            if (DialogObject.isEncryptedDialog(dialogId)) {
                List<RemoveAsReadMessage> messagesToCheck = dialogsToCheck.get("" + dialogId);
                if (messagesToCheck != null) {
                    for (RemoveAsReadMessage messageToCheck : messagesToCheck) {
                        if (messageToCheck.getId() == message.getId()) {
                            encryptedMessagesToDelete.put(dialogId, messageToCheck);
                        }
                    }
                }
            } else if (dialogLastReadIds.getOrDefault(dialogId, 0) < message.getId()) {
                dialogLastReadIds.put(dialogId, message.getId());
            }
        }
        for (Map.Entry<Long, Integer> entry : dialogLastReadIds.entrySet()) {
            readMaxIdUpdated(currentAccount, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, RemoveAsReadMessage> entry : encryptedMessagesToDelete.entrySet()) {
            startEncryptedDialogDeleteProcess(currentAccount, entry.getKey(), entry.getValue());
        }
    }

    public static void readMaxIdUpdated(int currentAccount, long currentDialogId, int readMaxId) {
        if (DialogObject.isEncryptedDialog(currentDialogId)) {
            return;
        }
        load();
        List<RemoveAsReadMessage> messagesToRemove = new ArrayList<>();
        messagesToRemoveAsRead.putIfAbsent("" + currentAccount, new HashMap<>());
        for (RemoveAsReadMessage messageToRemove : messagesToRemoveAsRead.get("" + currentAccount)
                .getOrDefault("" + currentDialogId, new ArrayList<>())) {
            if (messageToRemove.getId() <= readMaxId) {
                messagesToRemove.add(messageToRemove);
                messageToRemove.setReadTime(System.currentTimeMillis());
            }
        }
        save();
        startDeleteProcess(currentAccount, currentDialogId, messagesToRemove);
    }

    private static void startDeleteProcess(int currentAccount, long currentDialogId, List<RemoveAsReadMessage> messagesToRemove) {
        for (RemoveAsReadMessage messageToRemove : messagesToRemove) {
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageToRemove.getId());
                MessagesController.getInstance(currentAccount).deleteMessages(ids, null, null, currentDialogId,
                        true, false, false, 0,
                        null, false, false);
                cleanAutoDeletable(messageToRemove.getId(), currentAccount, currentDialogId);
            }, Math.max(messageToRemove.getScheduledTimeMs(), 0));
        }
    }

    public static void startEncryptedDialogDeleteProcess(int currentAccount, long currentDialogId, RemoveAsReadMessage messageToRemove) {
        AndroidUtilities.runOnUIThread(() -> {
            if (DialogObject.isEncryptedDialog(currentDialogId)) {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageToRemove.getId());
                ArrayList<Long> random_ids = new ArrayList<>();
                random_ids.add(messageToRemove.getRandomId());
                Integer encryptedChatId = DialogObject.getEncryptedChatId(currentDialogId);
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount)
                        .getEncryptedChat(encryptedChatId);

                MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids,
                        encryptedChat, currentDialogId, false, false,
                        false, 0, null, false, false);
            }
            cleanAutoDeletable(messageToRemove.getId(), currentAccount, currentDialogId);
        }, Math.max(messageToRemove.getScheduledTimeMs(), 0));
    }
}
