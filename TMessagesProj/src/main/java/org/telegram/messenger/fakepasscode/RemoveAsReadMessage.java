package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class RemoveAsReadMessage {
    private int id;
    private long topicId;
    private long randomId = -1; // from encrypted dialogs
    private long readTime = -1;
    private int sendTime = -1;
    private int scheduledTimeMs;

    public RemoveAsReadMessage() {
    }

    public RemoveAsReadMessage(int id, long topicId, long randomId, int sendTime, int scheduledTimeMs) {
        this.id = id;
        this.topicId = topicId;
        this.randomId = randomId;
        this.sendTime = sendTime;
        this.scheduledTimeMs = scheduledTimeMs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTopicId() {
        return topicId;
    }

    public int getSendTime() {
        return sendTime;
    }

    public void setSendTime(int sendTime) {
        this.sendTime = sendTime;
    }

    public long getRandomId() {
        return randomId;
    }

    public int getScheduledTimeMs() {
        return scheduledTimeMs;
    }

    @JsonIgnore
    public boolean isRead() {
        return readTime != -1;
    }

    public long getReadTime() {
        return readTime;
    }

    public void setReadTime(long readTime) {
        this.readTime = readTime;
    }

    public long calculateTargetTime() {
        return readTime + scheduledTimeMs;
    }
}
