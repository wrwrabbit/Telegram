package org.telegram.messenger.fakepasscode.results;

import java.util.ArrayList;
import java.util.List;

public class TelegramMessageResult {
    public static class Entry {
        public Entry() {
        }

        public Entry(long dialogId, int messageId) {
            this.dialogId = dialogId;
            this.messageId = messageId;
        }

        Long dialogId;
        int messageId;
    }

    private List<Entry> sosMessagesIds = new ArrayList<>();

    public void addMessage(long dialogId, int messageId) {
        sosMessagesIds.add(new Entry(dialogId, messageId));
    }

    public boolean isSosMessage(long dialogId, int messageId) {
        return sosMessagesIds.stream().anyMatch(e ->
                (e.dialogId == dialogId || e.dialogId == -dialogId) && e.messageId == messageId
        );
    }
}
