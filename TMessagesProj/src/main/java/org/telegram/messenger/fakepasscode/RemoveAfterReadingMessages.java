package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                String messagesToRemoveAsReadString = preferences.getString("messagesToRemoveAsRead", null);
                if (messagesToRemoveAsReadString != null) {
                    FileLog.d("[RemoveAfterReading] Messages loaded: " + messagesToRemoveAsReadString);
                    messagesToRemoveAsRead = SharedConfig.fromJson(messagesToRemoveAsReadString, HashMap.class);
                }
                String delaysString = preferences.getString("delays", null);
                if (delays != null) {
                    delays = SharedConfig.fromJson(delaysString, HashMap.class);
                }
                isLoaded = true;
            } catch (Exception e) {
                Utils.handleException(e);
            }
        }
    }

    public static void save() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                String messagesToRemoveAsReadString = SharedConfig.toJson(messagesToRemoveAsRead);
                FileLog.d("[RemoveAfterReading] Messages saved: " + messagesToRemoveAsReadString);
                String delaysString = SharedConfig.toJson(delays);
                editor.putString("messagesToRemoveAsRead", messagesToRemoveAsReadString);
                editor.putString("delays", delaysString);
                editor.commit();
            } catch (Exception e) {
                Utils.handleException(e);
            }
        }
    }

    public static void removeMessages(int accountNum, long dialogId, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        load();
        synchronized (sync) {
            List<RemoveAsReadMessage> messagesToRemove = getDialogMessagesToRemove(accountNum, dialogId);
            if (messagesToRemove == null) {
                return;
            }

            Set<Integer> messageIdsSet = new HashSet<>(messageIds);
            for (RemoveAsReadMessage messageToRemove : new ArrayList<>(messagesToRemove)) {
                if (messageIdsSet.contains(messageToRemove.getId())) {
                    messagesToRemove.remove(messageToRemove);
                    FileLog.d("[RemoveAfterReading] Message removed: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId());
                    break;
                }
            }

            if (messagesToRemove.isEmpty()) {
                messagesToRemoveAsRead.get("" + accountNum).remove("" + dialogId);
                FileLog.d("[RemoveAfterReading] Dialog removed: acc = " + accountNum + ", did = " + dialogId);
            }
        }
        save();
    }

    public static void checkReadDialogs(int accountNum) {
        FileLog.d("[RemoveAfterReading] checkReadDialogs: acc = " + accountNum);
        MessagesController controller = MessagesController.getInstance(accountNum);
        if (!controller.dialogsLoaded) {
            AndroidUtilities.runOnUIThread(() -> checkReadDialogs(accountNum), 100);
            FileLog.d("[RemoveAfterReading] checkReadDialogs: retry: acc = " + accountNum);
            return;
        }
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog == null) {
                continue;
            }
            List<RemoveAsReadMessage> messagesToCheck = getDialogMessagesToRemove(accountNum, dialog.id);
            if (messagesToCheck == null) {
                continue;
            }
            if (DialogObject.isEncryptedDialog(dialog.id)) {
                for (RemoveAsReadMessage messageToRemove : messagesToCheck) {
                    MessageLoader.load(accountNum, dialog.id, messageToRemove.getId(), message -> {
                        if (!message.isUnread()) {
                            FileLog.d("[RemoveAfterReading] checkReadDialogs: start encrypted deletion: acc = " + accountNum + ", did = " + dialog.id + ", mid = " + messageToRemove.getId());
                            startEncryptedDialogDeleteProcess(accountNum, dialog.id, messageToRemove);
                        }
                    });
                }
            } else {
                FileLog.d("[RemoveAfterReading] checkReadDialogs: max id update: acc = " + accountNum + ", did = " + dialog.id + ", mid = " + dialog.read_outbox_max_id);
                readMaxIdUpdated(accountNum, MessagesStorage.TopicKey.of(dialog.id, 0), dialog.read_outbox_max_id);
            }
        }
    }

    public static void notifyMessagesRead(int accountNum, Map<MessagesStorage.TopicKey, Integer> messages) {
        FileLog.d("[RemoveAfterReading] notifyMessagesRead: acc = " + accountNum);
        Map<MessagesStorage.TopicKey, Integer> dialogLastReadIds = new HashMap<>();
        Map<Long, List<RemoveAsReadMessage>> encryptedMessagesToDelete = new HashMap<>();
        for (Map.Entry<MessagesStorage.TopicKey, Integer> message : messages.entrySet()) {
            long dialogId = message.getKey().dialogId;
            int messageId = message.getValue();
            FileLog.d("[RemoveAfterReading] notifyMessagesRead: message: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageId);
            if (DialogObject.isEncryptedDialog(dialogId)) {
                List<RemoveAsReadMessage> messagesToCheck = getDialogMessagesToRemove(accountNum, dialogId);
                if (messagesToCheck == null) {
                    continue;
                }
                for (RemoveAsReadMessage messageToCheck : messagesToCheck) {
                    if (messageToCheck.getId() == messageId && !messageToCheck.isRead()) {
                        messageToCheck.setReadTime(System.currentTimeMillis());
                        encryptedMessagesToDelete.put(dialogId, new ArrayList<>());
                        encryptedMessagesToDelete.get(dialogId).add(messageToCheck);
                    }
                }
            } else if (dialogLastReadIds.getOrDefault(message.getKey(), 0) < messageId) {
                dialogLastReadIds.put(message.getKey(), messageId);
            }
        }
        for (Map.Entry<MessagesStorage.TopicKey, Integer> entry : dialogLastReadIds.entrySet()) {
            FileLog.d("[RemoveAfterReading] notifyMessagesRead: readMaxIdUpdated: acc = " + accountNum + ", did = " + entry.getKey().dialogId + ", mid = " + entry.getValue());
            readMaxIdUpdated(accountNum, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, List<RemoveAsReadMessage>> entry : encryptedMessagesToDelete.entrySet()) {
            long dialogId = entry.getKey();
            for (RemoveAsReadMessage removeAsReadMessage : entry.getValue()) {
                FileLog.d("[RemoveAfterReading] notifyMessagesRead: startEncryptedDialogDeleteProcess: acc = " + accountNum + ", did = " + dialogId + ", mid = " + removeAsReadMessage.getId());
                startEncryptedDialogDeleteProcess(accountNum, dialogId, removeAsReadMessage);
            }
        }
    }

    public static void readMaxIdUpdated(int accountNum, MessagesStorage.TopicKey key, int readMaxId) {
        List<RemoveAsReadMessage> dialogMessagesToRemove = getDialogMessagesToRemove(accountNum, key.dialogId);
        if (dialogMessagesToRemove == null || DialogObject.isEncryptedDialog(key.dialogId)) {
            return;
        }
        FileLog.d("[RemoveAfterReading] readMaxIdUpdated: acc = " + accountNum + ", did = " + key.dialogId + ", mid = " + readMaxId);
        List<RemoveAsReadMessage> messagesToRemove = new ArrayList<>();
        for (RemoveAsReadMessage messageToRemove : dialogMessagesToRemove) {
            if (!messageToRemove.isRead() && messageToRemove.getId() <= readMaxId && key.topicId == messageToRemove.getTopicId()) {
                messagesToRemove.add(messageToRemove);
                messageToRemove.setReadTime(System.currentTimeMillis());
                FileLog.d("[RemoveAfterReading] readMaxIdUpdated: setReadTime: acc = " + accountNum + ", did = " + key.dialogId + ", mid = " + messageToRemove.getId());
            }
        }
        save();
        startDeleteProcess(accountNum, key.dialogId, messagesToRemove);
    }

    private static void scheduleMessageDeletionRetry(int accountNum, long dialogId, RemoveAsReadMessage messageToRemove) {
        Utilities.globalQueue.postRunnable(() -> {
            List<RemoveAsReadMessage> remainingMessages =  getDialogMessagesToRemove(accountNum, dialogId);
            if (remainingMessages != null && remainingMessages.contains(messageToRemove)) {
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    startDeleteProcess(accountNum, dialogId, Collections.singletonList(messageToRemove));
                } else {
                    startEncryptedDialogDeleteProcess(accountNum, dialogId, messageToRemove);
                }
            }
        }, messageToRemove.calculateRemainingDelay() + 10_000);

    }

    private static void startDeleteProcess(int accountNum, long dialogId, List<RemoveAsReadMessage> messagesToRemove) {
        for (RemoveAsReadMessage messageToRemove : messagesToRemove) {
            FileLog.d("[RemoveAfterReading] startDeleteProcess: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId() + ", delay = " + messageToRemove.calculateRemainingDelay());
            if (!messageToRemove.isRead()) {
                FileLog.d("[RemoveAfterReading] startDeleteProcess: not read: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId() + ", delay = " + messageToRemove.calculateRemainingDelay());
                continue;
            }
            AndroidUtilities.runOnUIThread(() -> {
                FileLog.d("[RemoveAfterReading] startDeleteProcess: delete: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId());
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageToRemove.getId());
                MessagesController.getInstance(accountNum).deleteMessages(ids, null, null, dialogId,
                        true, false, false, 0,
                        null, false, false);
            }, messageToRemove.calculateRemainingDelay());
            scheduleMessageDeletionRetry(accountNum, dialogId, messageToRemove);
        }
    }

    public static void encryptedReadMaxTimeUpdated(int accountNum, long dialogId, int readMaxTime) {
        List<RemoveAsReadMessage> dialogMessagesToRemove = getDialogMessagesToRemove(accountNum, dialogId);
        if (dialogMessagesToRemove == null || !DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        FileLog.d("[RemoveAfterReading] encryptedReadMaxTimeUpdated: acc = " + accountNum + ", did = " + dialogId + ", time = " + readMaxTime);
        List<RemoveAsReadMessage> messagesToRemove = new ArrayList<>();
        for (RemoveAsReadMessage messageToRemove : dialogMessagesToRemove) {
            if (!messageToRemove.isRead() && messageToRemove.getSendTime() - 1 <= readMaxTime) {
                messagesToRemove.add(messageToRemove);
                messageToRemove.setReadTime(System.currentTimeMillis());
                FileLog.d("[RemoveAfterReading] encryptedReadMaxTimeUpdated: setReadTime: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId());
            }
        }
        save();
        for (RemoveAsReadMessage messageToRemove : messagesToRemove) {
            startEncryptedDialogDeleteProcess(accountNum, dialogId, messageToRemove);
        }
    }

    public static void startEncryptedDialogDeleteProcess(int accountNum, long dialogId, RemoveAsReadMessage messageToRemove) {
        if (!messageToRemove.isRead() || !DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        FileLog.d("[RemoveAfterReading] startEncryptedDialogDeleteProcess: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId() + ", delay = " + messageToRemove.calculateRemainingDelay());
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<Integer> ids = new ArrayList<>();
            ids.add(messageToRemove.getId());
            ArrayList<Long> random_ids = new ArrayList<>();
            random_ids.add(messageToRemove.getRandomId());
            Integer encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(accountNum)
                    .getEncryptedChat(encryptedChatId);
            FileLog.d("[RemoveAfterReading] startEncryptedDialogDeleteProcess: delete: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId());
            MessagesController.getInstance(accountNum).deleteMessages(ids, random_ids,
                    encryptedChat, dialogId, false, false,
                    false, 0, null, false, false);
        }, messageToRemove.calculateRemainingDelay());
        scheduleMessageDeletionRetry(accountNum, dialogId, messageToRemove);
    }

    public static void addMessageToRemove(int accountNum, long dialogId, RemoveAsReadMessage messageToRemove) {
        load();
        synchronized (sync) {
            FileLog.d("[RemoveAfterReading] addMessageToRemove: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId() + ", scheduledTime = " + messageToRemove.getScheduledTimeMs());
            messagesToRemoveAsRead.putIfAbsent("" + accountNum, new HashMap<>());
            Map<String, List<RemoveAsReadMessage>> dialogs = messagesToRemoveAsRead.get("" + accountNum);
            dialogs.putIfAbsent("" + dialogId, new ArrayList<>());
            dialogs.get("" + dialogId).add(messageToRemove);
        }
        save();
    }

    public static List<RemoveAsReadMessage> getDialogMessagesToRemove(int accountNum, long dialogId) {
        load();
        Map<String, List<RemoveAsReadMessage>> dialogs = messagesToRemoveAsRead.get("" + accountNum);
        return dialogs != null ? dialogs.get("" + dialogId) : null;
    }

    public static void updateMessage(int accountNum, long dialogId, int oldMessageId, int newMessageId, Integer newSendTime) {
        List<RemoveAsReadMessage> removeAsReadMessages = getDialogMessagesToRemove(accountNum, dialogId);
        if (removeAsReadMessages != null) {
            for (RemoveAsReadMessage message : removeAsReadMessages) {
                if (message.getId() == oldMessageId) {
                    message.setId(newMessageId);
                    if (newSendTime != null) {
                        message.setSendTime(newSendTime);
                    }
                    FileLog.d("[RemoveAfterReading] updateMessage: acc = " + accountNum + ", did = " + dialogId + ", old mid = " + oldMessageId + ", new mid = " + newMessageId + ", scheduledTime = " + message.getScheduledTimeMs());
                    RemoveAfterReadingMessages.save();
                    break;
                }
            }
        }
    }
}
