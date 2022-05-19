package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TelegramMessageAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {
    public static class Entry {
        public Entry() {}

        public Entry(long userId, String text, boolean addGeolocation) {
            this.userId = userId;
            this.text = text;
            this.addGeolocation = addGeolocation;
            this.dialogDeleted = false;
        }

        public long userId;
        public String text;
        public boolean addGeolocation;
        @JsonIgnore
        public boolean dialogDeleted = false;
    }

    public List<Entry> entries = new ArrayList<>();

    @Deprecated
    public Map<Integer, String> chatsToSendingMessages = new HashMap<>();

    @JsonIgnore
    private final Set<Integer> oldMessageIds = new HashSet<>();

    @JsonIgnore
    public List<Entry> sentEntries = new ArrayList<>();

    public TelegramMessageAction() {
    }

    @Override
    public void execute() {
        if ((chatsToSendingMessages.isEmpty() && entries.isEmpty())) {
            return;
        }
        FakePasscodeMessages.hasUnDeletedMessages.clear();
        FakePasscodeMessages.saveMessages();

        getMessagesController().forceResetDialogs();

        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogDeletedByAction);
        Map<String, FakePasscodeMessages.FakePasscodeMessage> unDeleted = new HashMap<>();
        for (Entry entry : entries) {
            FakePasscodeMessages.FakePasscodeMessage message = sendMessage(entry);
            if (message != null) {
                unDeleted.put("" + entry.userId, message);
            }
        }
        FakePasscodeMessages.hasUnDeletedMessages.put("" + accountNum, new HashMap<>(unDeleted));
        FakePasscodeMessages.saveMessages();
        sentEntries = entries;
        entries = new ArrayList<>();
        SharedConfig.saveConfig();
    }

    private FakePasscodeMessages.FakePasscodeMessage sendMessage(Entry entry) {
        FakePasscodeMessages.FakePasscodeMessage result = null;
        MessagesController controller = getMessagesController();
        String geolocation = Utils.getLastLocationString();
        String text = entry.text;
        if (entry.addGeolocation) {
            text += geolocation;
        }
        TLRPC.Dialog dialog = controller.dialogs_dict.get(entry.userId);
        TLRPC.Message oldMessage = null;
        if (dialog != null) {
            MessageObject messageObject = controller.dialogMessagesByIds.get(dialog.top_message);
            if (messageObject != null) {
                oldMessage = messageObject.messageOwner;
            }
        }
        getSendMessagesHelper().sendMessage(text, entry.userId, null, null, null, false,
                null, null, null, true, 0, null);
        entry.dialogDeleted = false;
        MessageObject msg = null;
        for (int i = 0; i < controller.dialogMessage.size(); ++i) {
            if (controller.dialogMessage.valueAt(i).messageText != null &&
                    text.contentEquals(controller.dialogMessage.valueAt(i).messageText)) {
                msg = controller.dialogMessage.valueAt(i);
                break;
            }
        }

        if (msg != null) {
            oldMessageIds.add(msg.getId());
            result = new FakePasscodeMessages.FakePasscodeMessage(entry.text, msg.messageOwner.date, oldMessage);
            deleteMessage(entry.userId, msg.getId());
        }
        return result;
    }

    private void deleteMessage(long chatId, int messageId) {
        MessagesController controller = getMessagesController();
        ArrayList<Integer> messages = new ArrayList<>();
        messages.add(messageId);
        if (ChatObject.isChannel(chatId, accountNum)) {
            controller.deleteMessages(messages, null, null, chatId,
                    false, false, false, 0, null, false, true);
        } else {
            controller.deleteMessages(messages, null, null, chatId,
                    false, false, false, 0, null, false, true);
        }
    }

    AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    SendMessagesHelper getSendMessagesHelper() {
        return getAccountInstance().getSendMessagesHelper();
    }

    NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }

        if (id == NotificationCenter.messageReceivedByServer) {
            messageReceivedByServer(args);
        } else if (id == NotificationCenter.dialogDeletedByAction) {
            dialogDeletedByAction(args);
        }
    }

    private void messageReceivedByServer(Object[] args) {
        int oldId = (int) args[0];
        TLRPC.Message message = (TLRPC.Message) args[2];
        if (message == null || !oldMessageIds.contains(oldId)) {
            return;
        }
        deleteMessage(message.dialog_id, message.id);
        Optional<Entry> entry = sentEntries.stream()
                .filter(e -> e.userId == message.dialog_id && e.dialogDeleted)
                .findFirst();
        if (entry.isPresent()) {
            AndroidUtilities.runOnUIThread(() -> Utils.deleteDialog(accountNum, entry.get().userId), 100);
            AndroidUtilities.runOnUIThread(() -> Utils.deleteDialog(accountNum, entry.get().userId), 1000);
        }

    }

    private void dialogDeletedByAction(Object[] args) {
        sentEntries.stream()
                .filter(entry -> entry.userId == (long)args[0])
                .forEach(entry -> entry.dialogDeleted = true);
    }

    @Override
    public void migrate() {
        if (!chatsToSendingMessages.isEmpty()) {
            entries = chatsToSendingMessages.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue(), false)).collect(Collectors.toList());
        }
    }
}
