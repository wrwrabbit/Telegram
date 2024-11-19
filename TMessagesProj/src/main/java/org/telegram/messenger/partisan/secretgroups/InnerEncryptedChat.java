package org.telegram.messenger.partisan.secretgroups;

public class InnerEncryptedChat {
    private final long userId;
    private Integer encryptedChatId;
    private InnerEncryptedChatState state;

    public InnerEncryptedChat(long userId, Integer encryptedChatId) {
        this.userId = userId;
        this.encryptedChatId = encryptedChatId;
        this.state = InnerEncryptedChatState.REQUEST_SENT;
    }

    public long getUserId() {
        return userId;
    }

    public Integer getEncryptedChatId() {
        return encryptedChatId;
    }

    public void setEncryptedChatId(int encryptedChatId) {
        this.encryptedChatId = encryptedChatId;
    }

    public InnerEncryptedChatState getState() {
        return state;
    }

    public void setState(InnerEncryptedChatState state) {
        this.state = state;
    }
}
