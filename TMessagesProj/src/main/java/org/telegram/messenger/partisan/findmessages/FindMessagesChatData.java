package org.telegram.messenger.partisan.findmessages;

import java.util.ArrayList;
import java.util.List;

public class FindMessagesChatData {
    public Long chatId;
    public String username;
    public Long linkedChatId;
    public String linkedUsername;
    public List<Integer> messageIds = new ArrayList<>();
}
