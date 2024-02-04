package org.telegram.messenger.partisan.findmessages;

import java.util.ArrayList;
import java.util.List;

public class FindMessagesItem {
    public Long chatId;
    public String username;
    public ArrayList<Integer> messageIds = new ArrayList<>();
}
