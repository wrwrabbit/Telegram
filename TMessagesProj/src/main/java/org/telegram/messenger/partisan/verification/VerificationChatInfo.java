package org.telegram.messenger.partisan.verification;

public class VerificationChatInfo {
    public long chatId;
    public String username;
    public int type;

    public VerificationChatInfo(long chatId, String username, int type) {
        this.chatId = chatId;
        this.username = username;
        this.type = type;
    }

    public VerificationChatInfo() {}
}
