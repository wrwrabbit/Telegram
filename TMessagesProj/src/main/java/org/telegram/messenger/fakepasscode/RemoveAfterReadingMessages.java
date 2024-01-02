package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoveAfterReadingMessages {
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
        RemoveAfterReadingMessages.load();
        Map<String, List<RemoveAsReadMessage>> curAccountMessages =
                RemoveAfterReadingMessages.messagesToRemoveAsRead.get("" + currentAccount);

        if (curAccountMessages == null || curAccountMessages.get("" + dialogId) == null) {
            return;
        }

        for (RemoveAsReadMessage messageToRemove : new ArrayList<>(curAccountMessages.get("" + dialogId))) {
            if (messageToRemove.getId() == messageId) {
                RemoveAfterReadingMessages.messagesToRemoveAsRead.get("" + currentAccount).get("" + dialogId).remove(messageToRemove);
            }
        }

        if (curAccountMessages.get("" + dialogId) != null
                && curAccountMessages.get("" + dialogId).isEmpty()) {
            RemoveAfterReadingMessages.messagesToRemoveAsRead.get("" + currentAccount).remove("" + dialogId);
        }
        RemoveAfterReadingMessages.save();
    }

    public static void checkReadDialogs(int currentAccount) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (!controller.dialogsLoaded) {
            AndroidUtilities.runOnUIThread(() -> checkReadDialogs(currentAccount), 100);
            return;
        }
        RemoveAfterReadingMessages.load();
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog != null) {
                startDeleteProcess(currentAccount, dialog.id, dialog.read_outbox_max_id);
            }
        }
    }

    public static void startDeleteProcess(int currentAccount, List<MessageObject> messages) {
        Map<Long, Integer> dialogLastReadIds = new HashMap<>();
        for (MessageObject message : messages) {
            long dialogId = message.messageOwner.dialog_id;
            if (dialogLastReadIds.getOrDefault(dialogId, 0) < message.getId()) {
                dialogLastReadIds.put(dialogId, message.getId());
            }
        }
        for (Map.Entry<Long, Integer> entry : dialogLastReadIds.entrySet()) {
            startDeleteProcess(currentAccount, entry.getKey(), entry.getValue());
        }
    }

    public static void startDeleteProcess(int currentAccount, long currentDialogId, int readMaxId) {
        RemoveAfterReadingMessages.load();
        List<RemoveAsReadMessage> messagesToRemove = new ArrayList<>();
        RemoveAfterReadingMessages.messagesToRemoveAsRead.putIfAbsent("" + currentAccount, new HashMap<>());
        for (RemoveAsReadMessage messageToRemove :
                RemoveAfterReadingMessages.messagesToRemoveAsRead.get("" + currentAccount)
                        .getOrDefault("" + currentDialogId, new ArrayList<>())) {
            if (messageToRemove.getId() <= readMaxId) {
                messagesToRemove.add(messageToRemove);
                messageToRemove.setReadTime(System.currentTimeMillis());
            }
        }
        RemoveAfterReadingMessages.save();

        for (RemoveAsReadMessage messageToRemove : messagesToRemove) {
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
                } else {
                    ArrayList<Integer> ids = new ArrayList<>();
                    ids.add(messageToRemove.getId());
                    MessagesController.getInstance(currentAccount).deleteMessages(ids, null, null, currentDialogId,
                            true, false, false, 0,
                            null, false, false);
                }
                cleanAutoDeletable(messageToRemove.getId(), currentAccount, currentDialogId);
            }, Math.max(messageToRemove.getScheduledTimeMs(), 0));
        }
        RemoveAfterReadingMessages.save();
    }
}
