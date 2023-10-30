package org.telegram.messenger.partisan.verification;

import java.util.ArrayList;
import java.util.List;

public class VerificationStorage {
    public String storageName;
    public String chatUsername;
    public long chatId;
    public long lastCheckTime;
    public int lastPostId;
    List<VerificationChatInfo> chats = new ArrayList<>();

    public VerificationStorage() {}
    public VerificationStorage(String storageName, String chatUsername, long chatId) {
        this.storageName = storageName;
        this.chatUsername = chatUsername;
        this.chatId = chatId;
    }
}
