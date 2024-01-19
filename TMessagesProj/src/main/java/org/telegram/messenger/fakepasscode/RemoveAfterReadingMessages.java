package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.content.SharedPreferences;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

    private static class WaitingEntry {
        public int accountNum;
        public long dialogId;
        public RemoveAsReadMessage message;
        public WaitingEntry(int accountNum, long dialogId, RemoveAsReadMessage message) {
            this.accountNum = accountNum;
            this.dialogId = dialogId;
            this.message = message;
        }
    }

    public static Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead = new HashMap<>();
    public static Map<String, Integer> delays = new HashMap<>();
    private static TreeMap<Long, List<WaitingEntry>> messagesWaitingToDelete = new TreeMap<>();
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
                    fillMessagesWaitingToDelete();
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
                    removeWaitingEntry(messageToRemove);
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
                            messageToRemove.setReadTime(System.currentTimeMillis());
                            addWaitingEntry(accountNum, dialog.id, messageToRemove);
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
                        addWaitingEntry(accountNum, dialogId, messageToCheck);
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
                addWaitingEntry(accountNum, key.dialogId, messageToRemove);
                FileLog.d("[RemoveAfterReading] readMaxIdUpdated: setReadTime: acc = " + accountNum + ", did = " + key.dialogId + ", mid = " + messageToRemove.getId());
            }
        }
        save();
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
                addWaitingEntry(accountNum, dialogId, messageToRemove);
                FileLog.d("[RemoveAfterReading] encryptedReadMaxTimeUpdated: setReadTime: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId());
            }
        }
        save();
    }

    public static void addMessageToRemove(int accountNum, long dialogId, RemoveAsReadMessage messageToRemove) {
        load();
        synchronized (sync) {
            FileLog.d("[RemoveAfterReading] addMessageToRemove: acc = " + accountNum + ", did = " + dialogId + ", mid = " + messageToRemove.getId() + ", scheduledTime = " + messageToRemove.getScheduledTimeMs());
            messagesToRemoveAsRead.computeIfAbsent("" + accountNum, k -> new HashMap<>())
                    .computeIfAbsent("" + dialogId, k -> new ArrayList<>())
                    .add(messageToRemove);
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

    private static void fillMessagesWaitingToDelete() {
        for (Map.Entry<String, Map<String, List<RemoveAsReadMessage>>> accEntry : messagesToRemoveAsRead.entrySet()) {
            for (Map.Entry<String, List<RemoveAsReadMessage>> dialogEntry : accEntry.getValue().entrySet()) {
                for (RemoveAsReadMessage message : dialogEntry.getValue()) {
                    if (message.isRead()) {
                        int accountNum = Integer.parseInt(accEntry.getKey());
                        long dialogId = Long.parseLong(dialogEntry.getKey());
                        addWaitingEntry(accountNum, dialogId, message);
                    }
                }
            }
        }
    }

    private static void addWaitingEntry(int accountNum, long dialogId, RemoveAsReadMessage messageToRemove) {
        synchronized (sync) {
            if (!messageToRemove.isRead()) {
                return;
            }
            messagesWaitingToDelete
                    .computeIfAbsent(messageToRemove.calculateTargetTime(), k -> new ArrayList<>())
                    .add(new WaitingEntry(accountNum, dialogId, messageToRemove));
        }
    }

    private static void removeWaitingEntry(RemoveAsReadMessage messageToRemove) {
        synchronized (sync) {
            if (!messageToRemove.isRead()) {
                return;
            }
            List<WaitingEntry> entries = messagesWaitingToDelete.get(messageToRemove.calculateTargetTime());
            if (entries != null) {
                entries.removeIf(e -> e.message == messageToRemove);
                if (entries.isEmpty()) {
                    messagesWaitingToDelete.remove(messageToRemove.calculateTargetTime());
                }
            }
        }
    }

    public static void runChecker() {
        checkMessagesWaitingToDelete();
    }

    private static void checkMessagesWaitingToDelete() {
        Map<Integer, Map<Long, List<RemoveAsReadMessage>>> messagesToDelete = new HashMap<>();
        for (Map.Entry<Long, List<WaitingEntry>> pair : messagesWaitingToDelete.entrySet()) {
            long targetTime = pair.getKey();
            if (targetTime > System.currentTimeMillis()) {
                break;
            }
            List<WaitingEntry> entries = pair.getValue();
            for (WaitingEntry entry : entries) {
                messagesToDelete.computeIfAbsent(entry.accountNum, k -> new HashMap<>())
                        .computeIfAbsent(entry.dialogId, k -> new ArrayList<>())
                        .add(entry.message);
            }
        }
        if (!messagesToDelete.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> deleteAllMessages(messagesToDelete));
        }
        Utilities.globalQueue.postRunnable(RemoveAfterReadingMessages::checkMessagesWaitingToDelete, 1000);
    }

    private static void deleteAllMessages(Map<Integer, Map<Long, List<RemoveAsReadMessage>>> messagesToRemove) {
        for (Map.Entry<Integer, Map<Long, List<RemoveAsReadMessage>>> accEntry : messagesToRemove.entrySet()) {
            for (Map.Entry<Long, List<RemoveAsReadMessage>> dialogEntry : accEntry.getValue().entrySet()) {
                int accountNum = accEntry.getKey();
                long dialogId = dialogEntry.getKey();
                List<RemoveAsReadMessage> messages = dialogEntry.getValue();
                deleteMessagesInDialog(accountNum, dialogId, messages);
            }
        }
    }

    private static void deleteMessagesInDialog(int accountNum, long dialogId, List<RemoveAsReadMessage> messagesToRemove) {
        boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);
        ArrayList<Long> random_ids = null;
        TLRPC.EncryptedChat encryptedChat = null;
        if (isEncrypted) {
            random_ids = messagesToRemove.stream()
                    .map(RemoveAsReadMessage::getRandomId)
                    .collect(Collectors.toCollection(ArrayList::new));
            Integer encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
            encryptedChat = MessagesController.getInstance(accountNum)
                    .getEncryptedChat(encryptedChatId);
        }
        FileLog.d("[RemoveAfterReading] deleteMessages: delete: acc = " + accountNum + ", did = " + dialogId);
        ArrayList<Integer> ids = messagesToRemove.stream()
                .map(RemoveAsReadMessage::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        MessagesController.getInstance(accountNum).deleteMessages(ids, random_ids,
                encryptedChat, dialogId, !isEncrypted, false, false, 0,
                null, false, false);
    }
}
