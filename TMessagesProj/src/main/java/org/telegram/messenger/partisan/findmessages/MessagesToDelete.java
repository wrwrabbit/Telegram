package org.telegram.messenger.partisan.findmessages;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessagesToDelete implements Iterable<FindMessagesChatData> {
    private final int accountNum;
    private final Map<Long, FindMessagesChatData> chatDataMap = new ConcurrentHashMap<>();

    MessagesToDelete(int accountNum) {
        this.accountNum = accountNum;
    }

    void add(FindMessagesChatData chatData) {
        chatDataMap.put(chatData.chatId, chatData);
    }

    void remove(long chatId) {
        chatDataMap.remove(chatId);
    }

    int getAccountNum() {
        return accountNum;
    }

    public boolean isEmpty() {
        return chatDataMap.isEmpty();
    }

    @NonNull
    @Override
    public Iterator<FindMessagesChatData> iterator() {
        return chatDataMap.values().iterator();
    }
}
