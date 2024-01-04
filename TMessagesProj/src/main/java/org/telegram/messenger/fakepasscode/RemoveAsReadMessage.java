package org.telegram.messenger.fakepasscode;

public class RemoveAsReadMessage {
    private int id;
    private long randomId = -1; // from encrypted dialogs
    private long readTime = -1;
    private int scheduledTimeMs;

    public RemoveAsReadMessage() {
    }

    public RemoveAsReadMessage(int id, long randomId, int scheduledTimeMs) {
        this.id = id;
        this.randomId = randomId;
        this.scheduledTimeMs = scheduledTimeMs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getRandomId() {
        return randomId;
    }

    public int getScheduledTimeMs() {
        return scheduledTimeMs;
    }

    public void setScheduledTimeMs(int scheduledTimeMs) {
        this.scheduledTimeMs = scheduledTimeMs;
    }

    public long getReadTime() {
        return readTime;
    }

    public void setReadTime(long readTime) {
        this.readTime = readTime;
    }
}
