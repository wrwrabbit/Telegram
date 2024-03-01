package org.telegram.messenger.partisan.findmessages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.partisan.PartisanLog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class MessagesToDeleteParser {
    private enum Mode {
        NULLABLE,
        NOT_NULL
    }

    private final int accountNum;
    private final byte[] filePayload;

    private MessagesToDeleteParser(int accountNum, byte[] filePayload) {
        this.accountNum = accountNum;
        this.filePayload = filePayload;
    }

    static MessagesToDelete parse(int accountNum, byte[] filePayload) {
        try {
            MessagesToDeleteParser parser = new MessagesToDeleteParser(accountNum, filePayload);
            return parser.parseJson();
        } catch (JSONException e) {
            PartisanLog.e("[FindMessages] parsing exception", e);
            throw new MessagesToDeleteLoadingException(e);
        }
    }

    private MessagesToDelete parseJson() throws JSONException {
        String jsonStr = new String(filePayload, StandardCharsets.UTF_8);
        JSONArray arr = new JSONArray(jsonStr);
        MessagesToDelete messagesToDelete = new MessagesToDelete(accountNum);
        for (int i = 0; i < arr.length(); i++) {
            FindMessagesChatData chatData = parseItem(arr.getJSONObject(i));
            messagesToDelete.add(chatData);
        }
        return messagesToDelete;
    }

    private static FindMessagesChatData parseItem(JSONObject obj) throws JSONException {
        FindMessagesChatData chatData = new FindMessagesChatData();
        chatData.chatId = getLongFromJson(obj, "chat_id", Mode.NOT_NULL);
        chatData.username = getStringFromJson(obj, "chat_username", Mode.NULLABLE);
        chatData.linkedChatId = getLongFromJson(obj, "linked_chat_id", Mode.NULLABLE);
        chatData.linkedUsername = getStringFromJson(obj, "linked_chat_username", Mode.NULLABLE);
        chatData.messageIds.addAll(getIntegerList(obj, "message_ids"));
        fixChatDataIds(chatData);
        PartisanLog.d("[FindMessages] parsed chatData for chatId = " + chatData.chatId + "." +
                " Message count = " + chatData.messageIds.size());
        return chatData;
    }

    private static String getStringFromJson(JSONObject obj, String key, Mode mode) throws JSONException {
        if (mode == Mode.NOT_NULL || obj.has(key) && !obj.isNull(key)) {
            return obj.getString(key);
        } else {
            return null;
        }
    }

    private static Long getLongFromJson(JSONObject obj, String key, Mode mode) throws JSONException {
        if (mode == Mode.NOT_NULL || obj.has(key) && !obj.isNull(key)) {
            return obj.getLong(key);
        } else {
            return null;
        }
    }

    private static void fixChatDataIds(FindMessagesChatData chatData) {
        chatData.chatId = -chatData.chatId;
        if (chatData.linkedChatId != null) {
            chatData.linkedChatId = -chatData.linkedChatId;
        }
    }

    private static List<Integer> getIntegerList(JSONObject obj, String key) throws JSONException {
        List<Integer> result = new ArrayList<>();
        JSONArray messageIdsArray = obj.getJSONArray("message_ids");
        for (int j = 0; j < messageIdsArray.length(); j++) {
            result.add(messageIdsArray.getInt(j));
        }
        return result;
    }
}
